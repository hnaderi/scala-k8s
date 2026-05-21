/*
 * Copyright 2022 Hossein Naderi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.hnaderi.k8s.client
package http4s

import cats.effect.kernel.Concurrent
import cats.syntax.all._
import dev.hnaderi.k8s.jawn.jawnFacade
import dev.hnaderi.k8s.utils._
import fs2.Stream
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.Headers
import org.http4s.Query
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.websocket._
import org.typelevel.jawn.Facade
import org.typelevel.jawn.Parser.parseFromByteArray
import scodec.bits.ByteVector

final class Http4sCombinedBackend[F[_], T] private (
    client: Client[F],
    wsClient: WSClient[F]
)(implicit
    F: Concurrent[F],
    enc: EntityEncoder[F, T],
    dec: EntityDecoder[F, T],
    builder: Builder[T],
    reader: Reader[T]
) extends Http4sBackend[F, T](client)
    with ExecBackend[Stream[F, *]] {

  private implicit val jawn: Facade.SimpleFacade[T] = jawnFacade[T]

  override def execConnect(
      url: String,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Stream[F, ExecInput] => Stream[F, ExecEvent] = { input =>
    val buildUri = F.fromEither(Uri.fromString(url)).map { uri =>
      val wsScheme = uri.scheme.map { s =>
        Uri.Scheme.unsafeFromString(
          if (s.value.equalsIgnoreCase("https")) "wss"
          else if (s.value.equalsIgnoreCase("http")) "ws"
          else s.value
        )
      }
      uri.copy(
        scheme = wsScheme,
        query = Query(params.map { case (k, v) => (k, Some(v)) }: _*)
      )
    }

    Stream.eval(buildUri).flatMap { wsUri =>
      val req = WSRequest(
        wsUri,
        Headers(
          headers ++ cookieHeader(cookies) ++ List(
            "Sec-WebSocket-Protocol" -> "v4.channel.k8s.io"
          )
        ),
        org.http4s.Method.GET
      )

      Stream.resource(wsClient.connectHighLevel(req)).flatMap { conn =>
        val receive: Stream[F, ExecEvent] =
          conn.receiveStream
            .collect { case WSFrame.Binary(data, _) if data.nonEmpty => data }
            .flatMap { data =>
              (data.head.toInt & 0xff) match {
                case 1 => Stream.emit(ExecEvent.Stdout(data.tail.toArray))
                case 2 => Stream.emit(ExecEvent.Stderr(data.tail.toArray))
                case 3 =>
                  val event = for {
                    t <- parseFromByteArray[T](data.tail.toArray).toOption
                    s <- t.decodeTo[Status].toOption
                  } yield ExecEvent.Error(s)
                  event match {
                    case Some(e) => Stream.emit(e)
                    case None    => Stream.empty
                  }
                case _ => Stream.empty
              }
            }
            .takeThrough { case _: ExecEvent.Error => false; case _ => true }

        val send: Stream[F, Nothing] =
          input
            .map {
              case ExecInput.Stdin(bytes) =>
                WSFrame.Binary(ByteVector(0x00.toByte) ++ ByteVector(bytes))
              case ExecInput.Resize(cols, rows) =>
                WSFrame.Binary(
                  ByteVector(0x04.toByte) ++
                    ByteVector(
                      s"""{"Width":$cols,"Height":$rows}""".getBytes("UTF-8")
                    )
                )
            }
            .through(conn.sendPipe)
            .drain

        receive.concurrently(send)
      }
    }
  }

  private def cookieHeader(
      cookies: Seq[(String, String)]
  ): List[(String, String)] =
    if (cookies.isEmpty) Nil
    else
      List("Cookie" -> cookies.map { case (k, v) => s"$k=$v" }.mkString("; "))
}

object Http4sCombinedBackend {
  def fromClients[F[_]: Concurrent, T: Builder: Reader](
      client: Client[F],
      wsClient: WSClient[F]
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T]
  ): Http4sCombinedBackend[F, T] =
    new Http4sCombinedBackend[F, T](client, wsClient)
}
