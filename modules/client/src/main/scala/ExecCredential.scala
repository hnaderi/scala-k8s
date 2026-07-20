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

import dev.hnaderi.k8s.utils._

/** The `status` an exec credential plugin returns on stdout. Exactly one of a
  * bearer `token` or a `clientCertificateData`/`clientKeyData` pair is
  * expected.
  */
final case class ExecCredentialStatus(
    token: Option[String] = None,
    clientCertificateData: Option[String] = None,
    clientKeyData: Option[String] = None,
    expirationTimestamp: Option[String] = None
)

object ExecCredentialStatus {
  implicit val decoder: Decoder[ExecCredentialStatus] =
    new Decoder[ExecCredentialStatus] {
      override def apply[T: Reader](
          t: T
      ): Either[String, ExecCredentialStatus] = for {
        obj <- ObjectReader(t)
        token <- obj.readOpt[String]("token")
        cert <- obj.readOpt[String]("clientCertificateData")
        key <- obj.readOpt[String]("clientKeyData")
        exp <- obj.readOpt[String]("expirationTimestamp")
      } yield ExecCredentialStatus(
        token = token,
        clientCertificateData = cert,
        clientKeyData = key,
        expirationTimestamp = exp
      )
    }
}

/** The `ExecCredential` object an exec credential plugin prints on stdout. */
final case class ExecCredential(
    apiVersion: String,
    kind: String,
    status: Option[ExecCredentialStatus] = None
)

object ExecCredential {
  implicit val decoder: Decoder[ExecCredential] = new Decoder[ExecCredential] {
    override def apply[T: Reader](t: T): Either[String, ExecCredential] = for {
      obj <- ObjectReader(t)
      apiVersion <- obj.getString("apiVersion")
      kind <- obj.getString("kind")
      status <- obj.readOpt[ExecCredentialStatus]("status")
    } yield ExecCredential(apiVersion, kind, status)
  }
}

/** Pure helpers implementing the `client.authentication.k8s.io` credential
  * plugin protocol, independent of any effect system or process runner.
  */
object ExecCredentials {

  /** Builds the `KUBERNETES_EXEC_INFO` request as a value tree. Callers
    * serialize it to JSON with their backend's own JSON encoder (the library
    * keeps JSON printing pluggable), e.g. via `foldTo[T]`.
    *
    * @param exec
    *   the resolved exec configuration
    * @param cluster
    *   the cluster being connected to; only included when `provideClusterInfo`
    *   is true
    * @param caData
    *   base64 CA data to advertise in the cluster info, if available
    */
  def execInfo(
      exec: ExecConfig,
      cluster: Option[Cluster] = None,
      caData: Option[String] = None
  ): KSON = {
    import KSON._

    val clusterField: Option[(String, KSON)] =
      if (exec.provideClusterInfo.contains(true))
        cluster.map { c =>
          val fields = List[Option[(String, KSON)]](
            Some("server" -> KString(c.server)),
            caData
              .orElse(c.`certificate-authority-data`)
              .map(d => "certificate-authority-data" -> KString(d))
          ).flatten
          "cluster" -> KObj(fields)
        }
      else None

    val spec = KObj(
      List[Option[(String, KSON)]](
        Some("interactive" -> KBool(false)),
        clusterField
      ).flatten
    )

    KObj(
      List(
        "apiVersion" -> KString(exec.apiVersion),
        "kind" -> KString("ExecCredential"),
        "spec" -> spec
      )
    )
  }

  /** Folds an exec plugin's returned credentials back into an [[AuthInfo]] so
    * the existing SSL/auth machinery consumes them unchanged.
    *
    * The protocol returns `clientCertificateData`/`clientKeyData` as raw PEM,
    * whereas kubeconfig's `-data` fields (and thus every SSL builder here) are
    * base64-encoded PEM, so the certificate/key are base64-encoded on the way
    * in.
    */
  def merge(auth: AuthInfo, status: ExecCredentialStatus): AuthInfo =
    auth.copy(
      token = status.token.orElse(auth.token),
      `client-certificate-data` = status.clientCertificateData
        .map(Utils.base64)
        .orElse(auth.`client-certificate-data`),
      `client-key-data` =
        status.clientKeyData.map(Utils.base64).orElse(auth.`client-key-data`)
    )

  /** Validates a plugin response against the protocol and the request. */
  def validate(
      req: ExecConfig,
      resp: ExecCredential
  ): Either[String, ExecCredentialStatus] =
    for {
      _ <-
        if (resp.apiVersion == req.apiVersion) Right(())
        else
          Left(
            s"exec plugin returned apiVersion '${resp.apiVersion}', expected '${req.apiVersion}'"
          )
      _ <-
        if (resp.kind == "ExecCredential") Right(())
        else
          Left(
            s"exec plugin returned kind '${resp.kind}', expected 'ExecCredential'"
          )
      status <- resp.status.toRight("exec plugin response is missing 'status'")
      hasToken = status.token.exists(_.nonEmpty)
      hasCert = status.clientCertificateData.exists(_.nonEmpty) &&
        status.clientKeyData.exists(_.nonEmpty)
      _ <-
        if (hasToken && hasCert)
          Left("exec plugin returned both a token and client certificate data")
        else if (!hasToken && !hasCert)
          Left(
            "exec plugin returned neither a token nor client certificate/key data"
          )
        else Right(())
    } yield status
}
