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
package zio

import dev.hnaderi.k8s.zioJson._
import io.k8s.apimachinery.pkg.apis.meta.v1
import _root_.zio._
import _root_.zio.http._
import _root_.zio.json._
import _root_.zio.stream._

class ZIOExecBackend(client: Client, auth: Task[AuthenticationParams])
    extends ZIOStreamingBackend(client, auth)
    with ExecBackend[ZKStream] {

  private def rewriteToWs(url: http.URL): http.URL =
    url.scheme match {
      case Some(Scheme.HTTP) => url.scheme(Scheme.WS)
      case _                 => url.scheme(Scheme.WSS)
    }

  private def decodeFrame(data: Chunk[Byte]): Option[ExecEvent] =
    if (data.isEmpty) None
    else
      (data.head.toInt & 0xff) match {
        case 1 => Some(ExecEvent.Stdout(data.drop(1).toArray))
        case 2 => Some(ExecEvent.Stderr(data.drop(1).toArray))
        case 3 =>
          val str = new String(data.drop(1).toArray, "UTF-8")
          JsonDecoder[v1.Status]
            .decodeJson(str)
            .toOption
            .map(ExecEvent.Error(_))
        case _ => None
      }

  override def execConnect(
      url: String,
      params: Seq[(String, String)]
  ): ZKStream[ExecInput] => ZKStream[ExecEvent] = { input =>
    ZStream.unwrapScoped[Any] {
      for {
        a <- auth
        wsUrl <- urlFor(url, params ++ a.params).map(rewriteToWs)
        wsHeaders = headersFor(a.headers) ++ cookiesFor(a.cookies)

        events <- Queue.unbounded[Take[Throwable, ExecEvent]]

        wsConfig = WebSocketConfig.default.subProtocol(
          Some("v4.channel.k8s.io")
        )

        handler = Handler.webSocket { channel =>
          val sendInput = input
            .mapZIO[Any, Throwable, Unit] {
              case ExecInput.Stdin(bytes) =>
                channel.send(
                  ChannelEvent.Read(
                    WebSocketFrame.Binary(
                      Chunk.single(0x00.toByte) ++ Chunk.fromArray(bytes)
                    )
                  )
                )
              case ExecInput.Resize(cols, rows) =>
                val json =
                  s"""{"Width":$cols,"Height":$rows}""".getBytes("UTF-8")
                channel.send(
                  ChannelEvent.Read(
                    WebSocketFrame.Binary(
                      Chunk.single(0x04.toByte) ++ Chunk.fromArray(json)
                    )
                  )
                )
            }
            .runDrain

          // Netty/zio-http does not aggregate WebSocket fragments. A logical
          // exec message can arrive as Binary(isFinal=false) followed by one
          // or more Continuation frames; only the assembled payload starts
          // with the v4-channel byte, so we must buffer until isFinal=true
          // before decoding.
          def deliver(data: Chunk[Byte]): UIO[Unit] =
            decodeFrame(data) match {
              case Some(e) => events.offer(Take.single(e)).unit
              case None    => ZIO.unit
            }

          val receiveOutput =
            Ref.make[Chunk[Byte]](Chunk.empty).flatMap { buffer =>
              channel.receiveAll {
                case ChannelEvent.Read(frame @ WebSocketFrame.Binary(data)) =>
                  if (frame.isFinal) buffer.set(Chunk.empty) *> deliver(data)
                  else buffer.set(data)
                case ChannelEvent.Read(
                      frame @ WebSocketFrame.Continuation(data)
                    ) =>
                  if (frame.isFinal)
                    buffer
                      .getAndSet(Chunk.empty)
                      .flatMap(prev => deliver(prev ++ data))
                  else buffer.update(_ ++ data)
                case ChannelEvent.ExceptionCaught(cause) => ZIO.fail(cause)
                case _                                   => ZIO.unit
              }
            }

          ZIO
            .scoped[Any] {
              sendInput.forkScoped *> receiveOutput
            }
            .onExit { exit =>
              events.offer(exit.foldExit(Take.failCause(_), _ => Take.end))
            }
        }

        _ <- handler
          .withConfig(wsConfig)
          .connect(wsUrl, wsHeaders)
          .provideSomeEnvironment[Scope](_.add[Client](client))
          .tapErrorCause(c => events.offer(Take.failCause(c)).unit)
          .forkScoped

      } yield ZStream.fromQueue(events).flattenTake
    }
  }
}

object ZIOExecBackend {
  def make: ZLayer[Client, Nothing, ZIOExecBackend] = Scope.default >>> ZLayer {
    ZIO
      .service[Client]
      .map(new ZIOExecBackend(_, ZIO.succeed(AuthenticationParams.empty)))
  }
}
