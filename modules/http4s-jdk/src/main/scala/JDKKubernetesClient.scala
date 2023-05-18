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
import cats.syntax.all._
import fs2.io.file.Files
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient

import java.net.http
import java.net.http.HttpClient.Builder
import javax.net.ssl.SSLContext

final class JDKKubernetesClient[F[_]: Async: Files: Env] private (
    builder: Builder
) extends JVMPlatform[F] {

  override protected def buildClient: Resource[F, Client[F]] = from(b => b)

  override protected def buildWithSSLContext
      : SSLContext => Resource[F, Client[F]] = ssl =>
    from(_.sslContext(ssl)).preAllocate(
      Async[F].delay {
        // workaround for https://github.com/http4s/http4s-jdk-http-client/issues/200
        if (Runtime.version().feature() == 11) {
          val params = ssl.getDefaultSSLParameters()
          params.setProtocols(params.getProtocols().filter(_ != "TLSv1.3"))
        }
      }
    )

  private def from(customize: Builder => Builder) =
    Resource.eval(Async[F].executor.flatMap { exec =>
      Async[F].delay {
        val client = customize(builder)
          .executor(exec)
          .build()

        JdkHttpClient(client)
      }
    })

}

object JDKKubernetesClient {
  def apply[F[_]: Async: Files: Env]: JDKKubernetesClient[F] =
    new JDKKubernetesClient[F](http.HttpClient.newBuilder())

  def apply[F[_]: Async: Files: Env](builder: Builder): JDKKubernetesClient[F] =
    new JDKKubernetesClient[F](builder)
}
