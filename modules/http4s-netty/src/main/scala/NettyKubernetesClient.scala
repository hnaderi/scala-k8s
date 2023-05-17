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
import org.http4s.client.Client
import org.http4s.netty.client.NettyClientBuilder

import javax.net.ssl.SSLContext

final class NettyKubernetesClient[F[_]: Async: Files: Env] private (
    builder: NettyClientBuilder[F]
) extends JVMPlatform[F] {

  override protected def buildClient: Resource[F, Client[F]] =
    builder.resource

  override protected def buildWithSSLContext
      : SSLContext => Resource[F, Client[F]] =
    builder.withSSLContext(_).resource

}

object NettyKubernetesClient {
  def apply[F[_]: Async: Files: Env]: NettyKubernetesClient[F] =
    new NettyKubernetesClient(NettyClientBuilder[F])
  def apply[F[_]: Async: Files: Env](
      builder: NettyClientBuilder[F]
  ): NettyKubernetesClient[F] = new NettyKubernetesClient(builder)
}
