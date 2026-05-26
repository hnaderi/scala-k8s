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
import org.typelevel.jawn.AsyncParser

import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher

class JDKStreamingBackend[T: Builder: Reader: Printer](
    client: java.net.http.HttpClient
) extends JDKBackend[T](client)
    with StreamingBackend[Flow.Publisher] {

  override def connectRaw(
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[Byte] =
    deferred[Byte] { out =>
      streamBytes(url, verb, headers, params, cookies, out) { (buf, push) =>
        while (buf.hasRemaining) push(buf.get())
      }
    }

  override def connectLines(
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[String] =
    deferred[String] { out =>
      val splitter = new LineSplitter
      streamBytes(url, verb, headers, params, cookies, out) { (buf, push) =>
        splitter.feed(buf, push)
      }
    }

  override def connect[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[O] =
    deferred[O] { out =>
      val parser = AsyncParser[T](AsyncParser.ValueStream)
      streamBytes(url, verb, headers, params, cookies, out) { (buf, push) =>
        parser.absorb(buf)(jawnF) match {
          case Left(err) =>
            out.closeExceptionally(
              new JDKBackend.DecodeError(err.getMessage)
            )
          case Right(values) =>
            values.foreach { t =>
              t.decodeTo[O] match {
                case Right(v) => push(v)
                case Left(e)  =>
                  out.closeExceptionally(new JDKBackend.DecodeError(e))
              }
            }
        }
      }
    }

  private def deferred[A](
      start: SubmissionPublisher[A] => Unit
  ): Flow.Publisher[A] =
    new Flow.Publisher[A] {
      override def subscribe(s: Flow.Subscriber[? >: A]): Unit = {
        val sp = new SubmissionPublisher[A]()
        sp.subscribe(s)
        start(sp)
      }
    }

  private def streamBytes[A](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)],
      out: SubmissionPublisher[A]
  )(
      onChunk: (ByteBuffer, A => Unit) => Unit
  ): Unit = {
    val req = JDKBackend
      .request(url, verb, headers, params, cookies, None)
      .build()
    val push: A => Unit = a => { out.submit(a); () }

    client
      .sendAsync(req, HttpResponse.BodyHandlers.ofPublisher())
      .whenComplete { (resp, err) =>
        if (err != null) out.closeExceptionally(err)
        else if (resp.statusCode() >= 300) {
          drainError[T](resp)
            .whenComplete { (ex, _) =>
              out.closeExceptionally(
                if (ex != null) ex else new RuntimeException("Request failed")
              )
            }
          ()
        } else
          resp
            .body()
            .subscribe(new ChunkSubscriber[A](out, onChunk, push))
      }
    ()
  }

  private def drainError[U: Builder: Reader](
      resp: HttpResponse[Flow.Publisher[java.util.List[ByteBuffer]]]
  ): java.util.concurrent.CompletableFuture[Throwable] = {
    val buf = new java.io.ByteArrayOutputStream()
    val done =
      new java.util.concurrent.CompletableFuture[Throwable]()
    resp
      .body()
      .subscribe(new Flow.Subscriber[java.util.List[ByteBuffer]] {
        override def onSubscribe(s: Flow.Subscription): Unit =
          s.request(Long.MaxValue)
        override def onNext(item: java.util.List[ByteBuffer]): Unit = {
          val it = item.iterator()
          while (it.hasNext) {
            val b = it.next()
            val arr = new Array[Byte](b.remaining())
            b.get(arr)
            buf.write(arr)
          }
        }
        override def onError(t: Throwable): Unit = { done.complete(t); () }
        override def onComplete(): Unit = {
          done.complete(
            JDKBackend.toErrorResponse[U](resp.statusCode(), buf.toByteArray)
          )
          ()
        }
      })
    done
  }

  private final class ChunkSubscriber[A](
      out: SubmissionPublisher[A],
      onChunk: (ByteBuffer, A => Unit) => Unit,
      push: A => Unit
  ) extends Flow.Subscriber[java.util.List[ByteBuffer]] {
    @volatile private var sub: Flow.Subscription = _
    override def onSubscribe(s: Flow.Subscription): Unit = {
      sub = s
      s.request(Long.MaxValue)
    }
    override def onNext(item: java.util.List[ByteBuffer]): Unit = {
      try {
        val it = item.iterator()
        while (it.hasNext) onChunk(it.next(), push)
      } catch {
        case e: Throwable =>
          out.closeExceptionally(e)
          if (sub != null) sub.cancel()
      }
    }
    override def onError(t: Throwable): Unit = out.closeExceptionally(t)
    override def onComplete(): Unit = out.close()
  }
}

object JDKStreamingBackend {
  def apply[T: Builder: Reader: Printer](
      client: java.net.http.HttpClient
  ): JDKStreamingBackend[T] = new JDKStreamingBackend[T](client)
}
