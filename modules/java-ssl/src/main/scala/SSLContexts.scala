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

package dev.hnaderi.k8s
package client

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl._

import Conversions._

private[client] object SSLContexts {
  private val TrustStoreSystemProperty = "javax.net.ssl.trustStore"
  private val TrustStorePasswordSystemProperty =
    "javax.net.ssl.trustStorePassword"
  private val KeyStoreSystemProperty = "javax.net.ssl.keyStore"
  private val KeyStorePasswordSystemProperty = "javax.net.ssl.keyStorePassword"

  def from(
      cluster: Cluster,
      auth: AuthInfo
  ): SSLContext = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      keyManagers(
        clientCertData = auth.`client-certificate-data`,
        clientCert = auth.`client-certificate`.map(new File(_)),
        clientKeyData = auth.`client-key-data`,
        clientKey = auth.`client-key`.map(new File(_)),
        clientKeyPass = None // TODO is it in the kubectl?
      ),
      trustManagers(
        caData = cluster.`certificate-authority-data`,
        caFile = cluster.`certificate-authority`.map(new File(_))
      ),
      new SecureRandom()
    )

    sslContext
  }

  def fromFile(
      ca: Option[File] = None,
      clientCert: Option[File] = None,
      clientKey: Option[File] = None,
      clientKeyPassword: Option[String] = None
  ): SSLContext = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      keyManagers(
        clientCert = clientCert,
        clientKey = clientKey,
        clientKeyPass = clientKeyPassword
      ),
      trustManagers(
        caFile = ca
      ),
      new SecureRandom()
    )

    sslContext
  }

  private def trustManagers(
      caData: Option[String] = None,
      caFile: Option[File]
  ) = {
    val certDataStream = caData.map(data =>
      new ByteArrayInputStream(Base64.getDecoder.decode(data))
    )
    val certFileStream = caFile.map(new FileInputStream(_))

    certDataStream.orElse(certFileStream).foreach { certStream =>
      val certificateFactory = CertificateFactory.getInstance("X509")
      val certificates =
        certificateFactory.generateCertificates(certStream).asScala
      certificates
        .map(_.asInstanceOf[X509Certificate])
        .zipWithIndex
        .foreach { case (certificate, i) =>
          val alias = s"${certificate.getSubjectX500Principal.getName}-$i"
          defaultTrustStore.setCertificateEntry(alias, certificate)
        }
    }

    val trustManagerFactory =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(defaultTrustStore)
    trustManagerFactory.getTrustManagers
  }

  private lazy val defaultTrustStore = {
    val securityDirectory = s"${System.getProperty("java.home")}/lib/security"

    val propertyTrustStoreFile =
      Option(System.getProperty(TrustStoreSystemProperty, ""))
        .filter(_.nonEmpty)
        .map(new File(_))
    val jssecacertsFile =
      Option(new File(s"$securityDirectory/jssecacerts")).filter(f =>
        f.exists && f.isFile
      )
    val cacertsFile = new File(s"$securityDirectory/cacerts")

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(
      new FileInputStream(
        propertyTrustStoreFile.orElse(jssecacertsFile).getOrElse(cacertsFile)
      ),
      System
        .getProperty(TrustStorePasswordSystemProperty, "changeit")
        .toCharArray
    )
    keyStore
  }

  private def keyManagers(
      clientCert: Option[File],
      clientCertData: Option[String] = None,
      clientKey: Option[File],
      clientKeyData: Option[String] = None,
      clientKeyPass: Option[String]
  ) = {
    // Client certificate
    val certDataStream = clientCertData.map(data =>
      new ByteArrayInputStream(Base64.getDecoder.decode(data))
    )
    val certFileStream = clientCert.map(new FileInputStream(_))

    // Client key
    val keyDataStream = clientKeyData.map(data =>
      new ByteArrayInputStream(Base64.getDecoder.decode(data))
    )
    val keyFileStream = clientKey.map(new FileInputStream(_))

    for {
      keyStream <- keyDataStream.orElse(keyFileStream)
      certStream <- certDataStream.orElse(certFileStream)
    } yield {
      Security.addProvider(new BouncyCastleProvider())
      val pemKeyPair = new PEMParser(new InputStreamReader(keyStream))
        .readObject()
        .asInstanceOf[PEMKeyPair] // scalafix:ok
      val privateKey = new JcaPEMKeyConverter()
        .setProvider("BC")
        .getPrivateKey(pemKeyPair.getPrivateKeyInfo)

      val certificateFactory = CertificateFactory.getInstance("X509")
      val certificate = certificateFactory
        .generateCertificate(certStream)
        .asInstanceOf[X509Certificate]

      defaultKeyStore.setKeyEntry(
        certificate.getSubjectX500Principal.getName,
        privateKey,
        clientKeyPass.fold(Array.empty[Char])(_.toCharArray),
        Array(certificate)
      )
    }

    val keyManagerFactory =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(defaultKeyStore, Array.empty)
    keyManagerFactory.getKeyManagers
  }

  private lazy val defaultKeyStore = {
    val propertyKeyStoreFile =
      Option(System.getProperty(KeyStoreSystemProperty, ""))
        .filter(_.nonEmpty)
        .map(new File(_))

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(
      propertyKeyStoreFile.map(new FileInputStream(_)).orNull,
      System.getProperty(KeyStorePasswordSystemProperty, "").toCharArray
    )
    keyStore
  }
}
