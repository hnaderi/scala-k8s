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
import org.http4s.client.websocket.WSClient

import javax.net.ssl.SSLContext

private[http4s] abstract class JVMExecPlatform[F[_]](implicit
    F: Async[F],
    Files: Files[F],
    Env: Env[F]
) extends JVMPlatform[F] {

  protected def buildWSClientWithSSLContext
      : SSLContext => Resource[F, WSClient[F]]

  final def fromConfigWithExec[T](
      config: Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KExecClient[F]] = {
    val currentContext = context.getOrElse(config.`current-context`)
    val toConnect = for {
      ctx <- config.contexts.find(_.name == currentContext)
      clusterName = cluster.getOrElse(ctx.context.cluster)
      cl <- config.clusters.find(_.name == clusterName)
      user <- config.users.find(_.name == ctx.context.user)
    } yield (cl.cluster, cl.cluster.server, user.user)

    toConnect match {
      case None =>
        Resource.eval(
          new IllegalArgumentException(
            "Cannot find where/how to connect using the provided config!"
          ).raiseError
        )
      case Some((clusterData, server, auth0)) =>
        for {
          resolved <- Http4sExec.resolve[F](auth0, clusterData)
          (auth, authenticator) = resolved
          ssl <- Resource.eval(F.blocking(SSLContexts.from(clusterData, auth)))
          httpClient <- buildWithSSLContext(ssl)
          wsClient <- buildWSClientWithSSLContext(ssl)
          backend = Http4sCombinedBackend.fromClients[F, T](
            httpClient,
            wsClient,
            authenticator
          )
        } yield HttpClient.withExec(server, backend)
    }
  }

  final def fromWithExec[T](
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
  ): Resource[F, KExecClient[F]] = {
    val sslContext = F.blocking(
      SSLContexts.fromFile(
        ca = ca.map(_.toNioPath.toFile),
        clientCert = clientCert.map(_.toNioPath.toFile),
        clientKey = clientKey.map(_.toNioPath.toFile),
        clientKeyPassword = clientKeyPassword
      )
    )
    for {
      ssl <- Resource.eval(sslContext)
      httpClient <- buildWithSSLContext(ssl)
      wsClient <- buildWSClientWithSSLContext(ssl)
      backend = Http4sCombinedBackend.fromClients[F, T](
        httpClient,
        wsClient,
        F.pure(authentication)
      )
    } yield HttpClient.withExec(server, backend)
  }

  final def loadWithExec[T](
      config: Path,
      context: Option[String] = None,
      cluster: Option[String] = None
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KExecClient[F]] =
    readConfigFile(config).flatMap(fromConfigWithExec(_, context, cluster))

  final def kubeconfigWithExec[T](
      context: Option[String] = None,
      cluster: Option[String] = None
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KExecClient[F]] =
    Resource.eval(kubeconfigPath).flatMap {
      case Some(value) => loadWithExec(value, context, cluster)
      case None        =>
        Resource.eval(F.raiseError(new NoKubeconfig))
    }

  final def defaultConfigWithExec[T](implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KExecClient[F]] =
    kubeconfigWithExec[T]().recoverWith { case _: NoKubeconfig =>
      podConfigWithExec[T]
    }

  final def podConfigWithExec[T](implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KExecClient[F]] =
    podServiceAccountAuth.flatMap(auth =>
      fromWithExec(
        server = podApiServer,
        ca = podCaCert.some,
        authentication = auth
      )
    )
}
