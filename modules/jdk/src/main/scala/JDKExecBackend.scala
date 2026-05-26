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
package jdk

import dev.hnaderi.k8s.utils._

import java.net.URI
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher

class JDKExecBackend[T: Builder: Reader: Printer](
    client: java.net.http.HttpClient
) extends JDKStreamingBackend[T](client)
    with ExecBackend[Flow.Publisher] {

  override def execConnect(
      url: String,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[ExecInput] => Flow.Publisher[ExecEvent] = { input =>
    new Flow.Publisher[ExecEvent] {
      override def subscribe(s: Flow.Subscriber[? >: ExecEvent]): Unit = {
        val out = new SubmissionPublisher[ExecEvent]()
        out.subscribe(s)
        startExec(url, headers, params, cookies, input, out)
      }
    }
  }

  private def startExec(
      url: String,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)],
      input: Flow.Publisher[ExecInput],
      out: SubmissionPublisher[ExecEvent]
  ): Unit = {
    val full = JDKBackend.buildUrl(url, params)
    val wsUri = URI.create(toWsScheme(full))

    val listener = new WebSocket.Listener {
      private val buf = new java.io.ByteArrayOutputStream()

      override def onOpen(ws: WebSocket): Unit = {
        ws.request(1)
        input.subscribe(new InputSubscriber(ws, out))
      }

      override def onBinary(
          ws: WebSocket,
          data: ByteBuffer,
          last: Boolean
      ): CompletionStage[?] = {
        while (data.hasRemaining) buf.write(data.get().toInt)
        if (last) {
          val frame = buf.toByteArray
          buf.reset()
          ExecFrame.decode[T](frame)(jawnF, implicitly).foreach { e =>
            out.submit(e); ()
          }
        }
        ws.request(1)
        null
      }

      override def onClose(
          ws: WebSocket,
          statusCode: Int,
          reason: String
      ): CompletionStage[?] = {
        out.close()
        null
      }

      override def onError(ws: WebSocket, error: Throwable): Unit =
        out.closeExceptionally(error)
    }

    val wsBuilder =
      client.newWebSocketBuilder().subprotocols("v4.channel.k8s.io")
    headers.foreach { case (k, v) => wsBuilder.header(k, v) }
    if (cookies.nonEmpty) {
      wsBuilder.header("Cookie", JDKBackend.cookieHeader(cookies))
      ()
    }

    wsBuilder
      .buildAsync(wsUri, listener)
      .whenComplete { (_, err) =>
        if (err != null) out.closeExceptionally(err)
      }
    ()
  }

  private def toWsScheme(url: String): String =
    if (url.startsWith("https://")) "wss://" + url.drop(8)
    else if (url.startsWith("http://")) "ws://" + url.drop(7)
    else url

  private final class InputSubscriber(
      ws: WebSocket,
      out: SubmissionPublisher[ExecEvent]
  ) extends Flow.Subscriber[ExecInput] {
    @volatile private var sub: Flow.Subscription = _
    private var pending: CompletableFuture[WebSocket] =
      CompletableFuture.completedFuture(ws)

    override def onSubscribe(s: Flow.Subscription): Unit = {
      sub = s
      s.request(1)
    }
    override def onNext(item: ExecInput): Unit = {
      pending = pending.thenCompose(_.sendBinary(ExecFrame.encode(item), true))
      pending.whenComplete { (_, err) =>
        if (err != null) {
          out.closeExceptionally(err)
          if (sub != null) sub.cancel()
        } else if (sub != null) sub.request(1)
      }
      ()
    }
    override def onError(t: Throwable): Unit = out.closeExceptionally(t)
    override def onComplete(): Unit = {
      pending.thenCompose(_.sendClose(WebSocket.NORMAL_CLOSURE, ""))
      ()
    }
  }
}

object JDKExecBackend {
  def apply[T: Builder: Reader: Printer](
      client: java.net.http.HttpClient
  ): JDKExecBackend[T] = new JDKExecBackend[T](client)
}
