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

import dev.hnaderi.k8s.utils.KSON._
import dev.hnaderi.k8s.utils._
import munit.FunSuite

class ExecConfigSuite extends FunSuite {

  private def decode[A: Decoder](k: KSON): A =
    k.decodeTo[A].fold(e => fail(s"decode failed: $e"), identity)

  test("decodes an exec block within an AuthInfo") {
    val user = KObj(
      Seq(
        "exec" -> KObj(
          Seq(
            "apiVersion" -> KString("client.authentication.k8s.io/v1beta1"),
            "command" -> KString("gke-gcloud-auth-plugin"),
            "args" -> KArr(Seq(KString("--version"))),
            "env" -> KArr(
              Seq(
                KObj(Seq("name" -> KString("FOO"), "value" -> KString("bar")))
              )
            ),
            "provideClusterInfo" -> KBool(true),
            "installHint" -> KString("install it"),
            "interactiveMode" -> KString("IfAvailable")
          )
        )
      )
    )

    val exec = decode[AuthInfo](user).exec.getOrElse(fail("exec not parsed"))
    assertEquals(exec.command, "gke-gcloud-auth-plugin")
    assertEquals(exec.apiVersion, "client.authentication.k8s.io/v1beta1")
    assertEquals(exec.args, Some(Seq("--version")))
    assertEquals(exec.env, Some(Seq(ExecEnvVar("FOO", "bar"))))
    assertEquals(exec.provideClusterInfo, Some(true))
    assertEquals(exec.installHint, Some("install it"))
    assertEquals(exec.interactiveMode, Some("IfAvailable"))
  }

  test("AuthInfo without an exec block decodes exec as None") {
    val ai = decode[AuthInfo](KObj(Seq("token" -> KString("abc"))))
    assertEquals(ai.exec, None)
    assertEquals(ai.token, Some("abc"))
  }

  test("decodes a token ExecCredential response") {
    val cred = decode[ExecCredential](
      KObj(
        Seq(
          "apiVersion" -> KString("client.authentication.k8s.io/v1beta1"),
          "kind" -> KString("ExecCredential"),
          "status" -> KObj(
            Seq(
              "token" -> KString("t0k3n"),
              "expirationTimestamp" -> KString("2020-03-05T14:39:00Z")
            )
          )
        )
      )
    )
    assertEquals(cred.status.flatMap(_.token), Some("t0k3n"))
    assertEquals(
      cred.status.flatMap(_.expirationTimestamp),
      Some("2020-03-05T14:39:00Z")
    )
  }

  test("decodes a client-certificate ExecCredential response") {
    val st = decode[ExecCredential](
      KObj(
        Seq(
          "apiVersion" -> KString("v1"),
          "kind" -> KString("ExecCredential"),
          "status" -> KObj(
            Seq(
              "clientCertificateData" -> KString("CERT"),
              "clientKeyData" -> KString("KEY")
            )
          )
        )
      )
    ).status.getOrElse(fail("status missing"))
    assertEquals(st.clientCertificateData, Some("CERT"))
    assertEquals(st.clientKeyData, Some("KEY"))
    assertEquals(st.token, None)
  }

  test("execInfo omits cluster when provideClusterInfo is not set") {
    val exec =
      ExecConfig(
        apiVersion = "client.authentication.k8s.io/v1beta1",
        command = "p"
      )
    assertEquals(
      ExecCredentials
        .execInfo(exec, Some(Cluster("https://1.2.3.4")))
        .printJson,
      """{"apiVersion":"client.authentication.k8s.io/v1beta1",""" +
        """"kind":"ExecCredential","spec":{"interactive":false}}"""
    )
  }

  test("execInfo includes cluster info when provideClusterInfo is true") {
    val exec = ExecConfig(
      apiVersion = "v1beta1",
      command = "p",
      provideClusterInfo = Some(true)
    )
    val cluster = Cluster(
      server = "https://1.2.3.4",
      `certificate-authority-data` = Some("Q0E=")
    )
    assertEquals(
      ExecCredentials.execInfo(exec, Some(cluster)).printJson,
      """{"apiVersion":"v1beta1","kind":"ExecCredential",""" +
        """"spec":{"interactive":false,"cluster":{"server":"https://1.2.3.4",""" +
        """"certificate-authority-data":"Q0E="}}}"""
    )
  }

  test("merge folds a token into AuthInfo.token") {
    val merged =
      ExecCredentials.merge(AuthInfo(), ExecCredentialStatus(token = Some("t")))
    assertEquals(merged.token, Some("t"))
  }

  test("merge base64-encodes the PEM cert/key into the AuthInfo data fields") {
    val merged = ExecCredentials.merge(
      AuthInfo(),
      ExecCredentialStatus(
        clientCertificateData = Some("CERT-PEM"),
        clientKeyData = Some("KEY-PEM")
      )
    )
    def decode(o: Option[String]): Option[String] =
      o.map(d => new String(java.util.Base64.getDecoder.decode(d), "UTF-8"))
    assertEquals(decode(merged.`client-certificate-data`), Some("CERT-PEM"))
    assertEquals(decode(merged.`client-key-data`), Some("KEY-PEM"))
  }

  private val execV1beta1 = ExecConfig(apiVersion = "v1beta1", command = "p")

  test("validate rejects an apiVersion mismatch") {
    val resp = ExecCredential(
      "v1alpha1",
      "ExecCredential",
      Some(ExecCredentialStatus(token = Some("t")))
    )
    assert(ExecCredentials.validate(execV1beta1, resp).isLeft)
  }

  test("validate rejects a missing status") {
    val resp = ExecCredential("v1beta1", "ExecCredential", None)
    assert(ExecCredentials.validate(execV1beta1, resp).isLeft)
  }

  test("validate rejects both token and certificate present") {
    val resp = ExecCredential(
      "v1beta1",
      "ExecCredential",
      Some(
        ExecCredentialStatus(
          token = Some("t"),
          clientCertificateData = Some("C"),
          clientKeyData = Some("K")
        )
      )
    )
    assert(ExecCredentials.validate(execV1beta1, resp).isLeft)
  }

  test("validate accepts a token-only status") {
    val resp = ExecCredential(
      "v1beta1",
      "ExecCredential",
      Some(ExecCredentialStatus(token = Some("t")))
    )
    assertEquals(
      ExecCredentials.validate(execV1beta1, resp).map(_.token),
      Right(Some("t"))
    )
  }
}
