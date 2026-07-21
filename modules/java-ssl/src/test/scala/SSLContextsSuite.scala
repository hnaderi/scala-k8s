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

import munit.FunSuite
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SSLContextsSuite extends FunSuite {

  // A self-signed CA, base64-encoded like kubeconfig's
  // `certificate-authority-data`.
  private val (caSubject, caData) = {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    val keyPair = gen.generateKeyPair()

    val name = new X500Name("CN=scala-k8s-test-ca")
    val now = System.currentTimeMillis()
    val builder = new JcaX509v3CertificateBuilder(
      name,
      BigInteger.valueOf(1),
      new Date(now - 60000),
      new Date(now + 3600000),
      name,
      keyPair.getPublic
    )
    val signer =
      new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate)
    val cert =
      new JcaX509CertificateConverter().getCertificate(builder.build(signer))

    (
      cert.getSubjectX500Principal.getName,
      Base64.getEncoder.encodeToString(cert.getEncoded)
    )
  }

  private def x509(tms: Array[TrustManager]): X509TrustManager =
    tms.collectFirst { case tm: X509TrustManager => tm }.get

  test("resolveTrustStore returns None when no security dir can be resolved") {
    // Simulates a GraalVM native image where `java.home` is null. This must not
    // throw a FileNotFoundException from a `null/lib/security/...` path.
    assume(
      Option(System.getProperty("javax.net.ssl.trustStore")).forall(_.isEmpty),
      "javax.net.ssl.trustStore is set in this JVM"
    )
    assertEquals(SSLContexts.resolveTrustStore(None), None)
  }

  test("trustManagers trust the cluster CA when no base store is resolvable") {
    // The native-image case with a cluster CA: an empty keystore is seeded with
    // the cluster CA, so the API server's CA is trusted.
    val tms =
      SSLContexts.trustManagersFrom(Some(caData), caFile = None, base = None)
    val issuers =
      x509(tms).getAcceptedIssuers.map(_.getSubjectX500Principal.getName).toList
    assert(
      issuers.contains(caSubject),
      s"expected accepted issuers to contain $caSubject, got $issuers"
    )
  }

  test("trustManagers fall back to the JVM default when no CA and no base") {
    // The native-image case with no cluster CA: `init(null)` defers to the
    // platform default trust store instead of failing.
    val tms = SSLContexts.trustManagersFrom(None, caFile = None, base = None)
    assert(
      x509(tms).getAcceptedIssuers.nonEmpty,
      "expected the JVM default trust store to provide accepted issuers"
    )
  }

  test("from builds a TLS context for a cluster with CA data") {
    val ctx = SSLContexts.from(
      Cluster(
        server = "https://example.test",
        `certificate-authority-data` = Some(caData)
      ),
      AuthInfo()
    )
    assertEquals(ctx.getProtocol, "TLS")
  }

  test("fromFile builds a TLS context with no CA") {
    assertEquals(SSLContexts.fromFile().getProtocol, "TLS")
  }
}
