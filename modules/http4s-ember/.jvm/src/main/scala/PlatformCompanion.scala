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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.io.net.tls.TLSContext
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

import javax.net.ssl.SSLContext

trait PlatformCompanion extends JVMPlatform { self: Http4sKubernetesClient =>
  override protected def buildWithSSLContext[F[_]: Async]
      : SSLContext => Resource[F, Client[F]] = ctx =>
    EmberClientBuilder
      .default[F]
      .withTLSContext(TLSContext.Builder.forAsync[F].fromSSLContext(ctx))
      .build
}
