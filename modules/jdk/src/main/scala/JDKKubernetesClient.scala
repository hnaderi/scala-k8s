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

import dev.hnaderi.k8s.manifest
import dev.hnaderi.k8s.utils._

import java.io.File
import java.io.FileNotFoundException
import java.net.http.{HttpClient => JHttpClient}
import java.nio.file.{Files => JFiles, Paths => JPaths}
import javax.net.ssl.SSLContext

object JDKKubernetesClient {

  private val serviceAccountBase =
    "/var/run/secrets/kubernetes.io/serviceaccount"
  private val podApiServer = "https://kubernetes.default.svc"

  private def buildClient(ssl: Option[SSLContext]): JHttpClient = {
    val b = JHttpClient.newBuilder()
    ssl.foreach(b.sslContext(_))
    b.build()
  }

  private def readFile(path: String): String =
    new String(JFiles.readAllBytes(JPaths.get(path)))

  private def kubeconfigPath(): Option[String] =
    Option(System.getenv("KUBECONFIG")).orElse {
      val home = Option(System.getProperty("user.home")).getOrElse("~")
      val p = s"$home/.kube/config"
      if (new File(p).exists()) Some(p) else None
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

  def make[T: Builder: Reader: Printer](
      url: String,
      sslContext: Option[SSLContext] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): JDKClient =
    HttpClient.streaming(
      url,
      new JDKStreamingBackend[T](buildClient(sslContext)),
      auth
    )

  def makeWithExec[T: Builder: Reader: Printer](
      url: String,
      sslContext: Option[SSLContext] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): JDKExecClient =
    HttpClient.withExec(
      url,
      new JDKExecBackend[T](buildClient(sslContext)),
      auth
    )

  def fromConfig[T: Builder: Reader: Printer](
      config: Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): JDKClient = {
    val (clusterData, server, auth) = resolveConfig(config, context, cluster)
    val ssl = SSLContexts.from(clusterData, auth)
    make[T](server, Some(ssl), AuthenticationParams.from(auth))
  }

  def fromConfigWithExec[T: Builder: Reader: Printer](
      config: Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): JDKExecClient = {
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
  ): JDKClient = {
    val ssl = SSLContexts.fromFile(
      ca = ca.map(new File(_)),
      clientCert = clientCert.map(new File(_)),
      clientKey = clientKey.map(new File(_)),
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
  ): JDKExecClient = {
    val ssl = SSLContexts.fromFile(
      ca = ca.map(new File(_)),
      clientCert = clientCert.map(new File(_)),
      clientKey = clientKey.map(new File(_)),
      clientKeyPassword = clientKeyPassword
    )
    makeWithExec[T](server, Some(ssl), auth)
  }

  def load[T: Builder: Reader: Printer](
      configFile: String,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): JDKClient = {
    val cfg = manifest
      .parse[Config](readFile(configFile))
      .fold(err => throw new IllegalArgumentException(err), identity)
    fromConfig[T](cfg, context, cluster)
  }

  def loadWithExec[T: Builder: Reader: Printer](
      configFile: String,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): JDKExecClient = {
    val cfg = manifest
      .parse[Config](readFile(configFile))
      .fold(err => throw new IllegalArgumentException(err), identity)
    fromConfigWithExec[T](cfg, context, cluster)
  }

  def kubeconfig[T: Builder: Reader: Printer](
      context: Option[String] = None,
      cluster: Option[String] = None
  ): JDKClient =
    kubeconfigPath()
      .map(load[T](_, context, cluster))
      .getOrElse(throw new FileNotFoundException("No kubeconfig found"))

  def kubeconfigWithExec[T: Builder: Reader: Printer](
      context: Option[String] = None,
      cluster: Option[String] = None
  ): JDKExecClient =
    kubeconfigPath()
      .map(loadWithExec[T](_, context, cluster))
      .getOrElse(throw new FileNotFoundException("No kubeconfig found"))

  def podConfig[T: Builder: Reader: Printer]: JDKClient = {
    val token = readFile(s"$serviceAccountBase/token")
    from[T](
      server = podApiServer,
      ca = Some(s"$serviceAccountBase/ca.crt"),
      auth = AuthenticationParams.bearer(token)
    )
  }

  def podConfigWithExec[T: Builder: Reader: Printer]: JDKExecClient = {
    val token = readFile(s"$serviceAccountBase/token")
    fromWithExec[T](
      server = podApiServer,
      ca = Some(s"$serviceAccountBase/ca.crt"),
      auth = AuthenticationParams.bearer(token)
    )
  }

  def defaultConfig[T: Builder: Reader: Printer]: JDKClient =
    try kubeconfig[T]()
    catch { case _: Throwable => podConfig[T] }

  def defaultConfigWithExec[T: Builder: Reader: Printer]: JDKExecClient =
    try kubeconfigWithExec[T]()
    catch { case _: Throwable => podConfigWithExec[T] }
}
