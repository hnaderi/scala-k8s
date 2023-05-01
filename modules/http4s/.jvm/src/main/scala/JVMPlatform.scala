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
import cats.effect.std.Env
import cats.syntax.all._
import dev.hnaderi.k8s.manifest
import dev.hnaderi.k8s.utils._
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s._
import org.http4s.client.Client

import java.io.FileNotFoundException
import javax.net.ssl.SSLContext

private[client] trait JVMPlatform { self: Http4sKubernetesClient =>
  protected def buildWithSSLContext[F[_]: Async]
      : SSLContext => Resource[F, Client[F]]

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

  def fromPath[F[_], T](
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

  def fromFile[F[_], T](
      config: String,
      context: Option[String] = None
  )(implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = fromPath(Path(config), context)

  def defaultConfig[F[_], T](implicit
      F: Async[F],
      env: Env[F],
      files: Files[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = {
    val homeConfig = System.getProperty("user.home") match {
      case null  => Path("~") / ".kube" / "config"
      case value => Path(value) / ".kube" / "config"
    }
    val envConfig = env.get("KUBECONFIG").map(_.map(Path(_)))

    val fromEnv: F[Option[Path]] = envConfig.flatMap {
      case None        => F.pure(None)
      case Some(value) => files.exists(value).ifF(Some(value), None)
    }

    val findFile = envConfig.flatMap {
      case Some(value) => value.pure
      case None =>
        files
          .exists(homeConfig)
          .ifM(
            homeConfig.pure,
            F.raiseError(
              new FileNotFoundException("Cannot find kubeconfig file!")
            )
          )
    }

    Resource.eval(findFile).flatMap(fromPath(_))
  }
}
