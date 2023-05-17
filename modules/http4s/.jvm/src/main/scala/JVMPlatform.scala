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
import dev.hnaderi.k8s.utils._
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s._
import org.http4s.client.Client

import javax.net.ssl.SSLContext

private[http4s] abstract class JVMPlatform[F[_]](implicit
    F: Async[F],
    Files: Files[F],
    Env: Env[F]
) extends Http4sKubernetesClient[F] {
  protected def buildWithSSLContext: SSLContext => Resource[F, Client[F]]

  /** Build kubernetes client from [[Config]] data structure
    *
    * @param config
    *   Config to use
    * @param context
    *   If provided, overrides the config's current context
    */
  final override def fromConfig[T](
      config: Config,
      context: Option[String] = None
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = {
    val currentContext = context.getOrElse(config.`current-context`)
    val toConnect = for {
      ctx <- config.contexts.find(_.name == currentContext)
      cluster <- config.clusters.find(_.name == ctx.context.cluster)
      user <- config.users.find(_.name == ctx.context.user)
    } yield (cluster.cluster, cluster.cluster.server, user.user)

    toConnect match {
      case None =>
        Resource.eval(
          new IllegalArgumentException(
            "Cannot find where/how to connect using the provided config!"
          ).raiseError
        )
      case Some((cluster, server, auth)) =>
        val sslContext = F.blocking(SSLContexts.from(cluster, auth))

        Resource
          .eval(sslContext)
          .flatMap(buildWithSSLContext)
          .map(Http4sBackend.fromClient(_))
          .map(HttpClient.streaming(server, _, AuthenticationParams.from(auth)))
    }

  }

  /** Build kubernetes client using the certificate files.
    *
    * @param server
    *   Server address
    * @param ca
    *   certificate authority file
    * @param clientCert
    *   client certificate file
    * @param clientKey
    *   client key file
    * @param clientKeyPassword
    *   password for client key if any
    * @param authentication
    *   Authentication parameters
    */
  final override def from[T](
      server: String,
      ca: Option[Path] = None,
      clientCert: Option[Path] = None,
      clientKey: Option[Path] = None,
      clientKeyPassword: Option[String] = None,
      authentication: AuthenticationParams = AuthenticationParams.empty
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = {
    val sslContext = F.blocking(
      SSLContexts.fromFile(
        ca = ca.map(_.toNioPath.toFile),
        clientCert = clientCert.map(_.toNioPath.toFile),
        clientKey = clientKey.map(_.toNioPath.toFile),
        clientKeyPassword = clientKeyPassword
      )
    )

    Resource
      .eval(sslContext)
      .flatMap(buildWithSSLContext)
      .map(Http4sBackend.fromClient(_))
      .map(HttpClient.streaming(server, _, authentication))

  }

}
