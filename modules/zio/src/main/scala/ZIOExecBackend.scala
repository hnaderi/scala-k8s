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

class ZIOExecBackend(client: Client)
    extends ZIOStreamingBackend(client)
    with ExecBackend[ZKStream] {

  private def rewriteToWs(url: http.URL): http.URL =
    url.scheme match {
      case Some(Scheme.HTTP) => url.scheme(Scheme.WS)
      case _                 => url.scheme(Scheme.WSS)
    }

  override def execConnect(
      url: String,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): ZKStream[ExecInput] => ZKStream[ExecEvent] = { input =>
    type WsChannel =
      Channel[ChannelEvent[WebSocketFrame], ChannelEvent[WebSocketFrame]]

    ZStream.asyncScoped[Any, Throwable, ExecEvent] { emit =>
      for {
        wsUrl <- urlFor(url, params).map(rewriteToWs)
        wsHeaders = Headers("Sec-WebSocket-Protocol" -> "v4.channel.k8s.io") ++
          headersFor(headers) ++ cookiesFor(cookies)

        handler = Handler.fromFunctionZIO[WsChannel] { channel =>
          val send = input.mapZIO {
            case ExecInput.Stdin(bytes) =>
              channel.send(
                ChannelEvent.Read(
                  WebSocketFrame.Binary(
                    Chunk.single(0x00.toByte) ++ Chunk.fromArray(bytes)
                  )
                )
              )
            case ExecInput.Resize(cols, rows) =>
              val json = s"""{"Width":$cols,"Height":$rows}""".getBytes("UTF-8")
              channel.send(
                ChannelEvent.Read(
                  WebSocketFrame.Binary(
                    Chunk.single(0x04.toByte) ++ Chunk.fromArray(json)
                  )
                )
              )
          }.runDrain

          val receive = channel
            .receiveAll {
              case ChannelEvent.Read(WebSocketFrame.Binary(data))
                  if data.nonEmpty =>
                (data.head.toInt & 0xff) match {
                  case 1 =>
                    val tail = data.drop(1).toArray
                    ZIO.succeed(
                      emit(ZIO.succeed(Chunk.single(ExecEvent.Stdout(tail))))
                    )
                  case 2 =>
                    val tail = data.drop(1).toArray
                    ZIO.succeed(
                      emit(ZIO.succeed(Chunk.single(ExecEvent.Stderr(tail))))
                    )
                  case 3 =>
                    val str = new String(data.drop(1).toArray, "UTF-8")
                    ZIO.succeed(
                      JsonDecoder[v1.Status].decodeJson(str).foreach { s =>
                        emit(ZIO.succeed(Chunk.single(ExecEvent.Error(s))))
                      }
                    )
                  case _ => ZIO.unit
                }
              case ChannelEvent.Unregistered =>
                ZIO.succeed(emit(ZIO.fail(None)))
              case _ => ZIO.unit
            }
            .ensuring(ZIO.succeed(emit(ZIO.fail(None))))

          send.race(receive)
        }

        _ <- WebSocketApp(handler)
          .connect(wsUrl, wsHeaders)
          .provideSomeEnvironment[Scope](_.add[Client](client))
          .forkScoped
      } yield ()
    }
  }
}

object ZIOExecBackend {
  def make: ZLayer[Client, Nothing, ZIOExecBackend] = Scope.default >>> ZLayer {
    ZIO.service[Client].map(new ZIOExecBackend(_))
  }
}
