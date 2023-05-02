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
import cats.syntax.all._
import dev.hnaderi.k8s.manifest
import dev.hnaderi.k8s.utils._
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s._
import org.http4s.client.Client

import java.io.File
import javax.net.ssl.SSLContext

private[client] trait JVMPlatform { self: Http4sKubernetesClient =>
  protected def buildWithSSLContext[F[_]: Async]
      : SSLContext => Resource[F, Client[F]]

  /** Build kubernetes client from [[Config]] data structure
    *
    * @param config
    *   Config to use
    * @param context
    *   If provided, overrides the config's current context
    */
  def fromConfig[F[_], T](
      config: Config,
      context: Option[String] = None
  )(implicit
      F: Async[F],
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
  def from[F[_], T](
      server: String,
      ca: Option[File] = None,
      clientCert: Option[File] = None,
      clientKey: Option[File] = None,
      clientKeyPassword: Option[String] = None,
      authentication: AuthenticationParams = AuthenticationParams.empty
  )(implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = {
    val sslContext = F.blocking(
      SSLContexts.fromFile(
        ca = ca,
        clientCert = clientCert,
        clientKey = clientKey,
        clientKeyPassword = clientKeyPassword
      )
    )

    Resource
      .eval(sslContext)
      .flatMap(buildWithSSLContext)
      .map(Http4sBackend.fromClient(_))
      .map(HttpClient.streaming(server, _, authentication))

  }

  /** Build kubernetes client from kubectl config file
    *
    * @param config
    *   Path to kubeconfig file
    * @param context
    *   If provided, overrides the config's current context
    */
  def load[F[_], T](
      config: Path,
      context: Option[String] = None
  )(implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = for {
    str <- Resource.eval(Files[F].readUtf8(config).compile.string)
    conf <- Resource.eval(F.fromEither(manifest.parse[Config](str)))
    client <- fromConfig(conf, context)
  } yield client

  /** Build kubernetes client from kubectl config file
    *
    * @param config
    *   Path to kubeconfig file
    * @param context
    *   If provided, overrides the config's current context
    */
  def loadFile[F[_], T](
      configFile: String,
      context: Option[String] = None
  )(implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = load(Path(configFile), context)

  /** Build kubernetes client kubectl config file found from default locations.
    * It tries:
    *   - `KUBECONFIG` from env
    *   - ~/.kube/config
    *   - pod's service account in /var/run/secrets/kubernetes.io/serviceaccount
    */
  def defaultConfig[F[_], T](implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = {

    Resource.eval(homeConfig[F]).flatMap {
      case Some(value) => load(value)
      case None        => podConfig[F, T]
    }
  }

  private def homeConfig[F[_]](implicit F: Async[F]) =
    F.delay {
      val homeConfig = System.getProperty("user.home") match {
        case null  => Path("~") / ".kube" / "config"
        case value => Path(value) / ".kube" / "config"
      }
      val envConfig = sys.env.get("KUBECONFIG").map(Path(_))

      envConfig.getOrElse(homeConfig)
    }.flatMap(p => Files[F].exists(p).ifF(Some(p), None))

  private def podConfig[F[_], T](implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = {
    val base = Path("/var/run/secrets/kubernetes.io/serviceaccount")
    val apiserver = "https://kubernetes.default.svc"
    val token = base / "token"
    val caCert = base / "ca.crt"
    val tokenAuth = Resource
      .eval(Files[F].readUtf8(token).compile.string)
      .map(AuthenticationParams.bearer(_))

    tokenAuth.flatMap(auth =>
      from(
        server = apiserver,
        ca = caCert.toNioPath.toFile.some,
        authentication = auth
      )
    )
  }
}
