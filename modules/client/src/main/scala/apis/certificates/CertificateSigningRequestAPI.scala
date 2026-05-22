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
package apis.certificates

import io.k8s.api.certificates.v1.CertificateSigningRequest
import io.k8s.api.certificates.v1.CertificateSigningRequestList

object CertificateSigningRequestAPI
    extends CertificatesV1.ClusterResourceAPI[
      CertificateSigningRequest,
      CertificateSigningRequestList
    ]("certificatesigningrequests") {

  case class ApprovalRequest(
      name: String,
      body: CertificateSigningRequest,
      dryRun: Option[String] = None,
      fieldManager: Option[String] = None,
      fieldValidation: Option[String] = None
  ) extends ReplaceRequest(
        s"${urlFor(name)}/approval",
        body,
        dryRun = dryRun,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation
      )

  def approve(
      name: String,
      csr: CertificateSigningRequest,
      dryRun: Option[String] = None,
      fieldManager: Option[String] = None,
      fieldValidation: Option[String] = None
  ): ApprovalRequest = ApprovalRequest(
    name,
    csr,
    dryRun = dryRun,
    fieldManager = fieldManager,
    fieldValidation = fieldValidation
  )

}
