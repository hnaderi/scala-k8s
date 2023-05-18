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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Env
import fs2.io.file.Files
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

final class EmberKubernetesClient[F[_]: Async: Network: Files: Env] private (
    builder: EmberClientBuilder[F]
) extends PlatformCompanion[F] {

  override protected def buildClient: Resource[F, Client[F]] =
    EmberClientBuilder.default[F].build

  protected def buildSecureClient(
      ctx: TLSContext[F]
  ): Resource[F, Client[F]] = builder.withTLSContext(ctx).build

}

object EmberKubernetesClient {
  def apply[F[_]: Async: Network: Files: Env]: EmberKubernetesClient[F] =
    new EmberKubernetesClient[F](EmberClientBuilder.default[F])

  def apply[F[_]: Async: Network: Files: Env](
      builder: EmberClientBuilder[F]
  ): EmberKubernetesClient[F] =
    new EmberKubernetesClient[F](builder)
}
