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

import dev.hnaderi.k8s.manifest
import dev.hnaderi.k8s.utils._
import sttp.client3._

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.net.ssl.SSLContext

import SttpKBackend.SttpF
import java.io.FileNotFoundException

private[client] trait SttpJVM[F[_]] {
  protected def buildWithSSLContext: SSLContext => SttpBackend[F, Any]

  /** Build kubernetes client from [[Config]] data structure
    *
    * @param config
    *   Config to use
    * @param context
    *   If provided, overrides the config's current context
    */
  def fromConfig[T: Builder: Reader: BodySerializer](
      config: Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): HttpClient[SttpF[F, *]] = {
    val currentContext = context.getOrElse(config.`current-context`)
    val toConnect = for {
      ctx <- config.contexts.find(_.name == currentContext)
      clusterName = cluster.getOrElse(ctx.context.cluster)
      cluster <- config.clusters.find(_.name == clusterName)
      user <- config.users.find(_.name == ctx.context.user)
    } yield (cluster.cluster, cluster.cluster.server, user.user)

    toConnect match {
      case None =>
        throw new IllegalArgumentException(
          "Cannot find where/how to connect using the provided config!"
        )
      case Some((cluster, server, auth)) =>
        val ssl = SSLContexts.from(cluster, auth)
        HttpClient[SttpF[F, *]](
          server,
          SttpKBackend[F, T](buildWithSSLContext(ssl)),
          AuthenticationParams.from(auth)
        )
    }

  }

  /** Build kubernetes client using the certificate files.
    *
    * @param server
    *   Server address
    * @param ca
    *   certificate authority file
    * @param clientCert
    *   client certificate file
    * @param clientKey
    *   client key file
    * @param clientKeyPassword
    *   password for client key if any
    * @param authentication
    *   Authentication parameters
    */
  def from[T: Builder: Reader: BodySerializer](
      server: String,
      ca: Option[File] = None,
      clientCert: Option[File] = None,
      clientKey: Option[File] = None,
      clientKeyPassword: Option[String] = None,
      authentication: AuthenticationParams = AuthenticationParams.empty
  ): HttpClient[SttpF[F, *]] = {
    val ssl = SSLContexts.fromFile(
      ca = ca,
      clientCert = clientCert,
      clientKey = clientKey,
      clientKeyPassword = clientKeyPassword
    )

    HttpClient[SttpF[F, *]](
      server,
      SttpKBackend[F, T](buildWithSSLContext(ssl)),
      authentication
    )
  }

  /** Build kubernetes client from kubectl config file
    *
    * @param config
    *   Path to kubeconfig file
    * @param context
    *   If provided, overrides the config's current context
    */
  def load[T: Builder: Reader: BodySerializer](
      config: Path,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): HttpClient[SttpF[F, *]] = {
    val str = readFile(config)
    manifest.parse[Config](str) match {
      case Left(error) => throw error
      case Right(config) =>
        fromConfig(config, context = context, cluster = cluster)
    }
  }

  /** Build kubernetes client from kubectl config file
    *
    * @param config
    *   Path to kubeconfig file
    * @param context
    *   If provided, overrides the config's current context
    */
  def loadFile[T: Builder: Reader: BodySerializer](
      config: String,
      context: Option[String] = None
  ): HttpClient[SttpF[F, *]] = load(Paths.get(config), context)

  /** Build kubernetes client kubectl config file found from default locations.
    * It tries:
    *   - `KUBECONFIG` from env
    *   - ~/.kube/config
    *   - pod's service account in /var/run/secrets/kubernetes.io/serviceaccount
    */
  def defaultConfig[T: Builder: Reader: BodySerializer]
      : HttpClient[SttpF[F, *]] = homeConfig match {
    case None         => podConfig[T]
    case Some(config) => load(config)
  }

  /** Build kubernetes client from kubectl config file found from default
    * locations. It tries:
    *   - `KUBECONFIG` from env
    *   - ~/.kube/config
    */
  def kubeconfig[T: Builder: Reader: BodySerializer](
      context: Option[String] = None,
      cluster: Option[String] = None
  ): HttpClient[SttpF[F, *]] = homeConfig match {
    case None => throw new FileNotFoundException("No kubeconfig found!")
    case Some(configPath) =>
      load(configPath, context = context, cluster = cluster)
  }

  import Conversions._
  private def readFile(path: Path): String =
    Files.readAllLines(path).asScala.mkString("\n")

  private def homeConfig: Option[Path] = {
    val homeConfig = System.getProperty("user.home") match {
      case null  => Paths.get("~", ".kube", "config")
      case value => Paths.get(value, ".kube", "config")
    }
    val envConfig = sys.env.get("KUBECONFIG").map(Paths.get(_))

    val f = envConfig.getOrElse(homeConfig)

    if (Files.exists(f)) Some(f) else None
  }

  /** Build kubernetes client from service account credentials inside pod from
    * /var/run/secrets/kubernetes.io/serviceaccount
    */
  final def podConfig[T: Builder: Reader: BodySerializer] = {
    val base = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount")
    val apiserver = "https://kubernetes.default.svc"
    val token = base.resolve("token")
    val caCert = base.resolve("ca.crt")
    val tokenAuth = AuthenticationParams.bearer(readFile(token))

    from(
      server = apiserver,
      ca = Some(caCert.toFile),
      authentication = tokenAuth
    )
  }
}
