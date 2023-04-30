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

import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import dev.hnaderi.k8s.utils._
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

object Http4sKubernetesClient {
  type KClient[F[_]] = HttpClient[F] with StreamingClient[Stream[F, *]]

  def fromClient[F[_], T](
      baseUrl: String,
      client: Client[F]
  )(implicit
      F: Concurrent[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): KClient[F] =
    HttpClient.streaming(baseUrl, Http4sBackend.fromClient(client))

  def fromUrl[F[_], T](
      baseUrl: String
  )(implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] =
    EmberClientBuilder.default[F].build.map(fromClient(baseUrl, _))
}
