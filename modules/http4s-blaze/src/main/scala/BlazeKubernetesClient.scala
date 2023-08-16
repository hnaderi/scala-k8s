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
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.{Client, Middleware}

import javax.net.ssl.SSLContext

final class BlazeKubernetesClient[F[_]: Async: Files: Env] private (
    builder: BlazeClientBuilder[F],
    middleware: Middleware[F]
) extends JVMPlatform[F] {

  override protected def buildClient: Resource[F, Client[F]] =
    builder.resource.map(middleware)

  override protected def buildWithSSLContext
      : SSLContext => Resource[F, Client[F]] =
    builder.withSslContext(_).resource.map(middleware)

}

object BlazeKubernetesClient {
  def apply[F[_]: Async: Files: Env]: BlazeKubernetesClient[F] =
    new BlazeKubernetesClient(BlazeClientBuilder[F], identity)
  def apply[F[_]: Async: Files: Env](
      builder: BlazeClientBuilder[F]
  ): BlazeKubernetesClient[F] = new BlazeKubernetesClient(builder, identity)

  def apply[F[_]: Async: Files: Env](
      builder: BlazeClientBuilder[F],
      middleware: Middleware[F]
  ): BlazeKubernetesClient[F] = new BlazeKubernetesClient(builder, middleware)
}
