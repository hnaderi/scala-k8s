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
import dev.hnaderi.k8s.utils._
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.net.tls.CertChainAndKey
import fs2.io.net.tls.S2nConfig
import fs2.io.net.tls.TLSContext
import org.http4s._
import org.http4s.client.Client
import scodec.bits.ByteVector
import cats.effect.std.Env

private[http4s] abstract class PlatformCompanion[F[_]: Async: Files: Env]
    extends Http4sKubernetesClient[F] {
  self: EmberKubernetesClient[F] =>

  private def dataOrFile(
      data: Option[String],
      file: Option[String]
  ): Resource[F, Option[ByteVector]] =
    Resource.eval(
      data
        .map(Stream.emit(_))
        .orElse(file.map(f => Files[F].readUtf8(Path(f))))
        .map(_.compile.string.map(ByteVector.fromBase64(_)))
        .getOrElse(Option.empty.pure)
    )

  private def client(
      caData: Option[String] = None,
      caFile: Option[String],
      clientCert: Option[String] = None,
      clientCertFile: Option[String],
      clientKey: Option[String] = None,
      clientKeyFile: Option[String]
  ): Resource[F, Client[F]] = for {
    ca <- dataOrFile(caData, caFile)
    cert <- dataOrFile(clientCert, clientCertFile)
    key <- dataOrFile(clientKey, clientKeyFile)

    certChain = (cert, key).mapN(CertChainAndKey(_, _)).toList

    config <- S2nConfig.builder
      .withCertChainAndKeysToStore(certChain)
      .withPemsToTrustStore(ca.map(_.decodeAsciiLenient).toList)
      .build[F]
    tls = TLSContext.Builder.forAsync[F].fromS2nConfig(config)
    client <- buildSecureClient(tls)
  } yield client

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
        client(
          caFile = cluster.`certificate-authority`,
          caData = cluster.`certificate-authority-data`,
          clientCert = auth.`client-certificate-data`,
          clientCertFile = auth.`client-certificate`,
          clientKey = auth.`client-key-data`,
          clientKeyFile = auth.`client-key`
        )
          .map(Http4sBackend.fromClient(_))
          .map(HttpClient.streaming(server, _, AuthenticationParams.from(auth)))
    }

  }

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
  ): Resource[F, KClient[F]] = client(
    caFile = ca.map(_.toString),
    clientCertFile = clientCert.map(_.toString),
    clientKeyFile = clientKey.map(_.toString)
    // clientKeyPass = clientKeyPassword TODO does it support key password?
  )
    .map(Http4sBackend.fromClient(_))
    .map(HttpClient.streaming(server, _, authentication))
}
