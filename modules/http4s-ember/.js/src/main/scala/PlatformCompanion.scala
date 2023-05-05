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
import fs2.io.net.tls.SecureContext
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.all._
import dev.hnaderi.k8s.utils._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import fs2.io.file.Files
import fs2.io.file.Path
import scodec.bits.ByteVector
import fs2.Chunk
import fs2.Stream

private[client] trait PlatformCompanion extends Http4sKubernetesClient {

  private def ssl[F[_]: Async](
      caData: Option[String] = None,
      caFile: Option[String],
      clientCert: Option[String] = None,
      clientCertFile: Option[String],
      clientKey: Option[String] = None,
      clientKeyFile: Option[String],
      clientKeyPass: Option[String] = None
  ): Resource[F, TLSContext[F]] = Resource.eval(
    for {
      ca <- dataOrFile(caData, caFile)
      cert <- dataOrFile(clientCert, clientCertFile)
      key <- dataOrFile(clientKey, clientKeyFile)
    } yield TLSContext.Builder
      .forAsync[F]
      .fromSecureContext(
        SecureContext(
          ca = ca.map(v => Seq(Left(v))),
          cert = cert.map(v => Seq(Left(v))),
          key = key.map(ck =>
            Seq(SecureContext.Key(Left(ck), passphrase = clientKeyPass))
          )
        )
      )
  )

  private def dataOrFile[F[_]: Async](
      data: Option[String],
      file: Option[String]
  ): F[Option[Chunk[Byte]]] = {
    def base64Data(str: Option[String]): Option[Chunk[Byte]] =
      str.flatMap(ByteVector.fromBase64(_).map(Chunk.byteVector))

    data
      .map(Stream.emit(_))
      .orElse(file.map(f => Files[F].readUtf8(Path(f))))
      .map(_.compile.string.map(Option(_)))
      .getOrElse(Option.empty.pure)
      .map(base64Data(_))
  }

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
        ssl(
          caFile = cluster.`certificate-authority`,
          caData = cluster.`certificate-authority-data`,
          clientCert = auth.`client-certificate-data`,
          clientCertFile = auth.`client-certificate`,
          clientKey = auth.`client-key-data`,
          clientKeyFile = auth.`client-key`
        ).flatMap(EmberClientBuilder.default[F].withTLSContext(_).build)
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
  ): Resource[F, KClient[F]] = ssl(
    caFile = ca.map(_.toString),
    clientCertFile = clientCert.map(_.toString),
    clientKeyFile = clientKey.map(_.toString),
    clientKeyPass = clientKeyPassword
  ).flatMap(EmberClientBuilder.default[F].withTLSContext(_).build)
    .map(Http4sBackend.fromClient(_))
    .map(HttpClient.streaming(server, _, authentication))

}
