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
import cats.effect.std.Env
import cats.syntax.all.*
import fs2.io.file.Files
import cats.effect.kernel.Resource
import org.http4s.client.{Client, Middleware}
import org.http4s.client.websocket.WSClient
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.jdkhttpclient.JdkWSClient

import java.net.http
import java.net.http.HttpClient.Builder
import javax.net.ssl.SSLContext

final class JDKKubernetesClient[F[_]: Async: Files: Env] private (
    builder: Builder,
    middleware: Middleware[F]
) extends JVMExecPlatform[F] {

  override protected def buildClient: Resource[F, Client[F]] = from(b => b)

  override protected def buildWithSSLContext
      : SSLContext => Resource[F, Client[F]] = ssl =>
    from(_.sslContext(ssl)).preAllocate(tls13Workaround(ssl))

  override protected def buildWSClientWithSSLContext
      : SSLContext => Resource[F, WSClient[F]] = ssl =>
    Resource
      .eval(
        Async[F].executor.flatMap { exec =>
          Async[F].delay(
            JdkWSClient[F](builder.sslContext(ssl).executor(exec).build())
          )
        }
      )
      .preAllocate(tls13Workaround(ssl))

  private def tls13Workaround(ssl: SSLContext): F[Unit] =
    // workaround for https://github.com/http4s/http4s-jdk-http-client/issues/200
    Async[F].delay {
      if (Runtime.version().feature() == 11) {
        val params = ssl.getDefaultSSLParameters()
        params.setProtocols(params.getProtocols().filter(_ != "TLSv1.3"))
      }
    }

  private def from(customize: Builder => Builder) =
    Resource
      .eval(Async[F].executor.flatMap { exec =>
        Async[F].delay {
          val client = customize(builder)
            .executor(exec)
            .build()

          JdkHttpClient(client)
        }
      })
      .map(middleware)

}

object JDKKubernetesClient {
  def apply[F[_]: Async: Files: Env]: JDKKubernetesClient[F] =
    new JDKKubernetesClient[F](http.HttpClient.newBuilder(), identity)

  def apply[F[_]: Async: Files: Env](builder: Builder): JDKKubernetesClient[F] =
    new JDKKubernetesClient[F](builder, identity)

  def apply[F[_]: Async: Files: Env](
      builder: Builder,
      middleware: Middleware[F]
  ): JDKKubernetesClient[F] =
    new JDKKubernetesClient[F](builder, middleware)
}
