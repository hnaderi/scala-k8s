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
package jdk

import dev.hnaderi.k8s.utils._

import javax.net.ssl.SSLContext

object FutureKubernetesClient {

  def make[T: Builder: Reader: Printer](
      url: String,
      sslContext: Option[SSLContext] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): FutureClient = {
    val httpClient = newHttp(sslContext)
    HttpClient.streaming(
      url,
      new FutureStreamingBackend[T](new JDKStreamingBackend[T](httpClient)),
      auth
    )
  }

  def makeWithExec[T: Builder: Reader: Printer](
      url: String,
      sslContext: Option[SSLContext] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): FutureExecClient = {
    val httpClient = newHttp(sslContext)
    HttpClient.withExec(
      url,
      new FutureExecBackend[T](new JDKExecBackend[T](httpClient)),
      auth
    )
  }

  def fromConfig[T: Builder: Reader: Printer](
      config: Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): FutureClient = {
    val (clusterData, server, auth) = resolveConfig(config, context, cluster)
    val ssl = SSLContexts.from(clusterData, auth)
    make[T](server, Some(ssl), AuthenticationParams.from(auth))
  }

  def fromConfigWithExec[T: Builder: Reader: Printer](
      config: Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): FutureExecClient = {
    val (clusterData, server, auth) = resolveConfig(config, context, cluster)
    val ssl = SSLContexts.from(clusterData, auth)
    makeWithExec[T](server, Some(ssl), AuthenticationParams.from(auth))
  }

  def from[T: Builder: Reader: Printer](
      server: String,
      ca: Option[String] = None,
      clientCert: Option[String] = None,
      clientKey: Option[String] = None,
      clientKeyPassword: Option[String] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): FutureClient = {
    val ssl = SSLContexts.fromFile(
      ca = ca.map(new java.io.File(_)),
      clientCert = clientCert.map(new java.io.File(_)),
      clientKey = clientKey.map(new java.io.File(_)),
      clientKeyPassword = clientKeyPassword
    )
    make[T](server, Some(ssl), auth)
  }

  def fromWithExec[T: Builder: Reader: Printer](
      server: String,
      ca: Option[String] = None,
      clientCert: Option[String] = None,
      clientKey: Option[String] = None,
      clientKeyPassword: Option[String] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): FutureExecClient = {
    val ssl = SSLContexts.fromFile(
      ca = ca.map(new java.io.File(_)),
      clientCert = clientCert.map(new java.io.File(_)),
      clientKey = clientKey.map(new java.io.File(_)),
      clientKeyPassword = clientKeyPassword
    )
    makeWithExec[T](server, Some(ssl), auth)
  }

  def load[T: Builder: Reader: Printer](
      configFile: String,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): FutureClient = {
    val cfg = dev.hnaderi.k8s.manifest
      .parse[Config](readFile(configFile))
      .fold(err => throw new IllegalArgumentException(err), identity)
    fromConfig[T](cfg, context, cluster)
  }

  def loadWithExec[T: Builder: Reader: Printer](
      configFile: String,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): FutureExecClient = {
    val cfg = dev.hnaderi.k8s.manifest
      .parse[Config](readFile(configFile))
      .fold(err => throw new IllegalArgumentException(err), identity)
    fromConfigWithExec[T](cfg, context, cluster)
  }

  def kubeconfig[T: Builder: Reader: Printer](
      context: Option[String] = None,
      cluster: Option[String] = None
  ): FutureClient =
    kubeconfigPath()
      .map(load[T](_, context, cluster))
      .getOrElse(
        throw new java.io.FileNotFoundException("No kubeconfig found")
      )

  def kubeconfigWithExec[T: Builder: Reader: Printer](
      context: Option[String] = None,
      cluster: Option[String] = None
  ): FutureExecClient =
    kubeconfigPath()
      .map(loadWithExec[T](_, context, cluster))
      .getOrElse(
        throw new java.io.FileNotFoundException("No kubeconfig found")
      )

  def podConfig[T: Builder: Reader: Printer]: FutureClient = {
    val token = readFile(s"$serviceAccountBase/token")
    from[T](
      server = podApiServer,
      ca = Some(s"$serviceAccountBase/ca.crt"),
      auth = AuthenticationParams.bearer(token)
    )
  }

  def podConfigWithExec[T: Builder: Reader: Printer]: FutureExecClient = {
    val token = readFile(s"$serviceAccountBase/token")
    fromWithExec[T](
      server = podApiServer,
      ca = Some(s"$serviceAccountBase/ca.crt"),
      auth = AuthenticationParams.bearer(token)
    )
  }

  def defaultConfig[T: Builder: Reader: Printer]: FutureClient =
    try kubeconfig[T]()
    catch { case _: Throwable => podConfig[T] }

  def defaultConfigWithExec[T: Builder: Reader: Printer]: FutureExecClient =
    try kubeconfigWithExec[T]()
    catch { case _: Throwable => podConfigWithExec[T] }

  private val serviceAccountBase =
    "/var/run/secrets/kubernetes.io/serviceaccount"
  private val podApiServer = "https://kubernetes.default.svc"

  private def newHttp(ssl: Option[SSLContext]): java.net.http.HttpClient = {
    val b = java.net.http.HttpClient.newBuilder()
    ssl.foreach(b.sslContext(_))
    b.build()
  }

  private def readFile(path: String): String =
    new String(
      java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path))
    )

  private def kubeconfigPath(): Option[String] =
    Option(System.getenv("KUBECONFIG")).orElse {
      val home = Option(System.getProperty("user.home")).getOrElse("~")
      val p = s"$home/.kube/config"
      if (new java.io.File(p).exists()) Some(p) else None
    }

  private def resolveConfig(
      config: Config,
      context: Option[String],
      cluster: Option[String]
  ): (Cluster, String, AuthInfo) = {
    val currentContext = context.getOrElse(config.`current-context`)
    val result = for {
      ctx <- config.contexts.find(_.name == currentContext)
      clusterName = cluster.getOrElse(ctx.context.cluster)
      cl <- config.clusters.find(_.name == clusterName)
      user <- config.users.find(_.name == ctx.context.user)
    } yield (cl.cluster, cl.cluster.server, user.user)

    result.getOrElse(
      throw new IllegalArgumentException(
        "Cannot find connection details in kubeconfig"
      )
    )
  }
}
