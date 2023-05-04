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

import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import dev.hnaderi.k8s.utils._
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import fs2.io.file.Files
import fs2.io.file.Path
import cats.syntax.all._
import dev.hnaderi.k8s.manifest
import cats.effect.std.Env

trait Http4sKubernetesClient {
  final type KClient[F[_]] = HttpClient[F] with StreamingClient[Stream[F, *]]

  protected def buildClient[F[_]: Async]: Resource[F, Client[F]]

  final def fromClient[F[_], T](
      baseUrl: String,
      client: Client[F]
  )(implicit
      F: Concurrent[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): KClient[F] =
    HttpClient.streaming(baseUrl, Http4sBackend.fromClient(client))

  final def fromUrl[F[_], T](
      baseUrl: String
  )(implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = buildClient[F].map(fromClient(baseUrl, _))

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
  def from[F[_], T](
      server: String,
      ca: Option[Path] = None,
      clientCert: Option[Path] = None,
      clientKey: Option[Path] = None,
      clientKeyPassword: Option[String] = None,
      authentication: AuthenticationParams = AuthenticationParams.empty
  )(implicit
      F: Async[F],
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
  final def load[F[_], T](
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
  final def loadFile[F[_], T](
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
  final def defaultConfig[F[_], T](implicit
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
    Env
      .make[F]
      .get("KUBECONFIG")
      .flatMap {
        case None =>
          F.delay(System.getProperty("user.home") match {
            case null  => Path("~") / ".kube" / "config"
            case value => Path(value) / ".kube" / "config"
          })
        case Some(value) => Path(value).pure
      }
      .flatMap(p => Files[F].exists(p).ifF(Some(p), None))

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
        ca = caCert.some,
        authentication = auth
      )
    )
  }
}
