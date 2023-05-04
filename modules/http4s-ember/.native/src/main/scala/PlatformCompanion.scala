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

import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.S2nConfig
import cats.effect.kernel.Async
import fs2.io.file.Files
import cats.effect.kernel.Resource
import fs2.Stream
import cats.syntax.all._
import dev.hnaderi.k8s.utils._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import fs2.io.file.Path
import scodec.bits.ByteVector
import fs2.io.net.tls.CertChainAndKey
import org.http4s.client.Client

private[client] trait PlatformCompanion extends Http4sKubernetesClient {
  private def dataOrFile[F[_]: Async](
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

  private def client[F[_]: Async](
      caData: Option[String] = None,
      caFile: Option[String] = None,
      clientCert: Option[String] = None,
      clientCertFile: Option[String] = None,
      clientKey: Option[String] = None,
      clientKeyFile: Option[String] = None
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
    client <- EmberClientBuilder.default[F].withTLSContext(tls).build
  } yield client

  final override def fromConfig[F[_], T](
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
        client[F](
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

  final override def from[F[_], T](
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
  ): Resource[F, KClient[F]] = client(
    caFile = ca.map(_.toString),
    clientCertFile = clientCert.map(_.toString),
    clientKeyFile = clientKey.map(_.toString)
    // clientKeyPass = clientKeyPassword TODO does it support key password?
  )
    .map(Http4sBackend.fromClient(_))
    .map(HttpClient.streaming(server, _, authentication))
}
