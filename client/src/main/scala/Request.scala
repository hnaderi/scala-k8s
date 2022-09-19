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

package dev.hnaderi.k8s
package client

import dev.hnaderi.k8s.utils._

sealed trait Request
trait HttpRequest[O] extends Request {
  def run[F[_]](http: HttpClient[F]): F[O]
}
trait WatchRequest[O] extends Request {
  def start[F[_]](http: StreamingClient[F]): F[O]
}

trait HttpClient[F[_]] {
  def get[O: Decoder](url: String, params: (String, String)*): F[O]
  def post[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O]
  def put[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O]
  def patch[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O]
  def delete[O: Decoder](url: String, params: (String, String)*): F[O]
}
trait StreamingClient[F[_]] {
  def connect[O: Decoder](url: String, params: (String, String)*): F[O]
}

trait KubernetesHttpClient[F[_]] {
  def send[O](req: HttpRequest[O]): F[O]
}
trait KubernetesWatchClient[F[_]] {
  def listen[O](req: WatchRequest[O]): F[O]
}

trait KubernetesClient[F[_], G[_]]
    extends KubernetesHttpClient[F]
    with KubernetesWatchClient[G]
