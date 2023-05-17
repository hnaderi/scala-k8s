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
import cats.syntax.all._
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient

import java.net.http
import javax.net.ssl.SSLContext
import cats.effect.std.Env
import fs2.io.file.Files

final class JDKKubernetesClient[F[_]: Async: Files: Env]
    extends JVMPlatform[F] {

  override protected def buildClient: Resource[F, Client[F]] =
    Resource.eval(JdkHttpClient.simple[F])

  override protected def buildWithSSLContext
      : SSLContext => Resource[F, Client[F]] = ssl =>
    Resource.eval(Async[F].executor.flatMap { exec =>
      Async[F].delay {
        val builder = http.HttpClient.newBuilder()
        // workaround for https://github.com/http4s/http4s-jdk-http-client/issues/200
        if (Runtime.version().feature() == 11) {
          val params = ssl.getDefaultSSLParameters()
          params.setProtocols(params.getProtocols().filter(_ != "TLSv1.3"))
        }

        val client = builder
          .sslContext(ssl)
          .executor(exec)
          .build()

        JdkHttpClient(client)
      }
    })

}
