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

import dev.hnaderi.k8s.utils.Decoder
import dev.hnaderi.k8s.utils.Encoder

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import scala.concurrent.Future
import scala.concurrent.Promise

class FutureBackend[T](
    underlying: JDKBackend[T]
) extends HttpBackend[Future] {
  override def send[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Future[O] =
    FutureBackend.toFuture(
      underlying.send[O](url, verb, headers, params, cookies)
    )

  override def send[I: Encoder, O: Decoder](
      url: String,
      verb: APIVerb,
      body: I,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Future[O] =
    FutureBackend.toFuture(
      underlying.send[I, O](url, verb, body, headers, params, cookies)
    )
}

object FutureBackend {
  private[jdk] def toFuture[A](cf: CompletableFuture[A]): Future[A] = {
    val p = Promise[A]()
    cf.whenComplete { (value, err) =>
      if (err != null) p.failure(err) else p.success(value)
    }
    p.future
  }
}

class FutureStreamingBackend[T](
    underlying: JDKStreamingBackend[T]
) extends FutureBackend[T](underlying)
    with StreamingBackend[Flow.Publisher] {

  override def connect[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[O] =
    underlying.connect[O](url, verb, headers, params, cookies)

  override def connectLines(
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[String] =
    underlying.connectLines(url, verb, headers, params, cookies)

  override def connectRaw(
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[Byte] =
    underlying.connectRaw(url, verb, headers, params, cookies)
}

class FutureExecBackend[T](
    underlying: JDKExecBackend[T]
) extends FutureStreamingBackend[T](underlying)
    with ExecBackend[Flow.Publisher] {

  override def execConnect(
      url: String,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Flow.Publisher[ExecInput] => Flow.Publisher[ExecEvent] =
    underlying.execConnect(url, headers, params, cookies)
}
