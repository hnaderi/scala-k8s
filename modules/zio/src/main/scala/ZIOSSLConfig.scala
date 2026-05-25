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
package zio

import _root_.zio._
import _root_.zio.http._

import java.nio.file.{Files => JFiles, Paths => JPaths}
import java.util.Base64

private[zio] object ZIOSSLConfig {

  private def base64Decode(data: String): Array[Byte] =
    Base64.getDecoder.decode(data.replaceAll("\\s+", ""))

  private def tempPemFile(data: Array[Byte]): ZIO[Scope, Throwable, String] =
    ZIO.acquireRelease(
      ZIO.attemptBlockingIO {
        val tmp = JFiles.createTempFile("k8s-", ".pem")
        JFiles.write(tmp, data)
        tmp.toString
      }
    )(path =>
      ZIO.attemptBlockingIO(JFiles.deleteIfExists(JPaths.get(path))).orDie
    )

  def fromClusterAndAuth(
      cluster: Cluster,
      auth: AuthInfo
  ): ZIO[Scope, Throwable, ClientSSLConfig] = {
    val hasClientCert =
      auth.`client-certificate`.isDefined || auth.`client-certificate-data`.isDefined
    val hasClientKey =
      auth.`client-key`.isDefined || auth.`client-key-data`.isDefined

    for {
      caCertPath <- (
        cluster.`certificate-authority`,
        cluster.`certificate-authority-data`
      ) match {
        case (Some(path), _)    => ZIO.succeed(Some(path))
        case (None, Some(data)) => tempPemFile(base64Decode(data)).map(Some(_))
        case (None, None)       => ZIO.succeed(None)
      }

      serverConfig: ClientSSLConfig = caCertPath match {
        case Some(path) => ClientSSLConfig.FromCertFile(path)
        case None       => ClientSSLConfig.Default
      }

      sslConfig <-
        if (hasClientCert && hasClientKey) {
          for {
            certPath <- (
              auth.`client-certificate`,
              auth.`client-certificate-data`
            ) match {
              case (Some(path), _)    => ZIO.succeed(path)
              case (None, Some(data)) => tempPemFile(base64Decode(data))
              case (None, None)       =>
                ZIO.fail(
                  new IllegalArgumentException("client-certificate missing")
                )
            }
            keyPath <- (auth.`client-key`, auth.`client-key-data`) match {
              case (Some(path), _)    => ZIO.succeed(path)
              case (None, Some(data)) => tempPemFile(base64Decode(data))
              case (None, None)       =>
                ZIO.fail(new IllegalArgumentException("client-key missing"))
            }
          } yield ClientSSLConfig.FromClientAndServerCert(
            serverConfig,
            ClientSSLCertConfig.FromClientCertFile(certPath, keyPath)
          )
        } else ZIO.succeed(serverConfig)
    } yield sslConfig
  }

  def fromFiles(
      ca: Option[String] = None,
      clientCert: Option[String] = None,
      clientKey: Option[String] = None
  ): ClientSSLConfig = {
    val serverConfig: ClientSSLConfig = ca match {
      case Some(path) => ClientSSLConfig.FromCertFile(path)
      case None       => ClientSSLConfig.Default
    }
    (clientCert, clientKey) match {
      case (Some(cert), Some(key)) =>
        ClientSSLConfig.FromClientAndServerCert(
          serverConfig,
          ClientSSLCertConfig.FromClientCertFile(cert, key)
        )
      case _ => serverConfig
    }
  }
}
