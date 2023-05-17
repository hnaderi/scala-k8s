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
import dev.hnaderi.k8s.manifest
import dev.hnaderi.k8s.utils._
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s._
import org.http4s.client.Client

private[http4s] abstract class Http4sKubernetesClient[F[_]](implicit
    F: Async[F],
    Files: Files[F],
    Env: Env[F]
) {
  protected def buildClient: Resource[F, Client[F]]

  final def fromClient[T](
      baseUrl: String,
      client: Client[F]
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): KClient[F] =
    HttpClient.streaming(baseUrl, Http4sBackend.fromClient(client))

  final def fromUrl[T](
      baseUrl: String
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = buildClient.map(fromClient(baseUrl, _))

  /** Build kubernetes client from [[Config]] data structure
    *
    * @param config
    *   Config to use
    * @param context
    *   If provided, overrides the config's current context
    */
  def fromConfig[T](
      config: Config,
      context: Option[String] = None
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]]

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
  def from[T](
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
  ): Resource[F, KClient[F]]

  /** Build kubernetes client from kubectl config file
    *
    * @param config
    *   Path to kubeconfig file
    * @param context
    *   If provided, overrides the config's current context
    */
  final def load[T](
      config: Path,
      context: Option[String] = None
  )(implicit
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = for {
    str <- Resource.eval(Files.readUtf8(config).compile.string)
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
  final def loadFile[T](
      configFile: String,
      context: Option[String] = None
  )(implicit
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
  final def defaultConfig[T](implicit
      F: Async[F],
      Files: Files[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] =
    Resource.eval(homeConfig).flatMap {
      case Some(value) => load(value)
      case None        => podConfig[T]
    }

  private def homeConfig =
    Env
      .get("KUBECONFIG")
      .flatMap {
        case None =>
          F.delay(System.getProperty("user.home") match {
            case null  => Path("~") / ".kube" / "config"
            case value => Path(value) / ".kube" / "config"
          })
        case Some(value) => Path(value).pure
      }
      .flatMap(p => Files.exists(p).ifF(Some(p), None))

  private def podConfig[T](implicit
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
      .eval(Files.readUtf8(token).compile.string)
      .map(AuthenticationParams.bearer(_))

    tokenAuth.flatMap(auth =>
      from(
        server = apiserver,
        ca = caCert.some,
        authentication = auth
      )
    )
  }
}
