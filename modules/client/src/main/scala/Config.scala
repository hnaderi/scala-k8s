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

import java.io.File

case class KubeConfig private (
    server: String,
    // authorization: Option[Authorization],
    caCertData: Option[String],
    caCertFile: Option[File],
    clientCertData: Option[String],
    clientCertFile: Option[File],
    clientKeyData: Option[String],
    clientKeyFile: Option[File],
    clientKeyPass: Option[String],
    authInfoExec: Option[AuthInfoExec]
)

case class Config(
    apiVersion: String,
    clusters: Seq[NamedCluster],
    contexts: Seq[NamedContext],
    `current-context`: String,
    users: Seq[NamedAuthInfo]
)

case class NamedCluster(name: String, cluster: Cluster)
case class Cluster(
    server: String,
    `certificate-authority`: Option[String] = None,
    `certificate-authority-data`: Option[String] = None
)

case class NamedContext(name: String, context: Context)
case class Context(
    cluster: String,
    user: String,
    namespace: Option[String] = None
)

case class NamedAuthInfo(name: String, user: AuthInfo)
case class AuthInfo(
    `client-certificate`: Option[String] = None,
    `client-certificate-data`: Option[String] = None,
    `client-key`: Option[String] = None,
    `client-key-data`: Option[String] = None,
    exec: Option[AuthInfoExec] = None
)
case class AuthInfoExec(
    apiVersion: String,
    command: String,
    env: Option[Map[String, String]],
    args: Option[Seq[String]],
    installHint: Option[String],
    provideClusterInfo: Option[Boolean],
    interactiveMode: Option[String]
)
case class ExecCredential(
    kind: String,
    apiVersion: String,
    status: ExecCredentialStatus
)
case class ExecCredentialStatus(
    expirationTimestamp: String,
    token: Option[String]
)
