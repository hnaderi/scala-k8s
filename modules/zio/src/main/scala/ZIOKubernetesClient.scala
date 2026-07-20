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

import dev.hnaderi.k8s.manifest
import _root_.zio._
import _root_.zio.http._
import _root_.zio.http.netty.NettyConfig

import java.io.FileNotFoundException
import java.nio.file.{Files => JFiles, Paths => JPaths}

object ZIOKubernetesClient {

  private val serviceAccountBase =
    "/var/run/secrets/kubernetes.io/serviceaccount"
  private val podApiServer = "https://kubernetes.default.svc"

  private def makeClient(
      sslConfig: ClientSSLConfig
  ): ZIO[Scope, Throwable, Client] = {
    // SSL must be baked into ZClient.Config rather than applied via
    // Client.ssl(...): zio-http's `socket` driver path uses the base config
    // directly and ignores the per-client SSL overlay, so WebSocket
    // connections would otherwise miss the client cert and get 401s.
    val cfg = ZClient.Config.default.ssl(sslConfig)
    val layer =
      (ZLayer.succeed(cfg) ++
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown) ++
        DnsResolver.default) >>> ZClient.live
    layer.build.map(_.get[Client])
  }

  private def readFile(path: String): ZIO[Any, Throwable, String] =
    ZIO.attemptBlockingIO(new String(JFiles.readAllBytes(JPaths.get(path))))

  private def kubeconfigPath: ZIO[Any, Throwable, Option[String]] =
    ZIO.attempt(Option(java.lang.System.getenv("KUBECONFIG"))).flatMap {
      case some @ Some(_) => ZIO.succeed(some)
      case None           =>
        ZIO.attempt {
          val home =
            Option(java.lang.System.getProperty("user.home")).getOrElse("~")
          val path = s"$home/.kube/config"
          if (new java.io.File(path).exists()) Some(path) else None
        }
    }

  private def podServiceAccountAuth: ZIO[Any, Throwable, AuthenticationParams] =
    readFile(s"$serviceAccountBase/token").map(AuthenticationParams.bearer)

  private def resolveConfig(
      config: dev.hnaderi.k8s.client.Config,
      context: Option[String],
      cluster: Option[String]
  ): ZIO[Any, Throwable, (Cluster, String, AuthInfo)] = {
    val currentContext = context.getOrElse(config.`current-context`)
    val result = for {
      ctx <- config.contexts.find(_.name == currentContext)
      clusterName = cluster.getOrElse(ctx.context.cluster)
      cl <- config.clusters.find(_.name == clusterName)
      user <- config.users.find(_.name == ctx.context.user)
    } yield (cl.cluster, cl.cluster.server, user.user)

    ZIO
      .fromOption(result)
      .orElseFail(
        new IllegalArgumentException(
          "Cannot find connection details in kubeconfig"
        )
      )
  }

  /** Build client from an already-constructed URL and optional auth. */
  def make(
      url: String,
      sslConfig: ClientSSLConfig = ClientSSLConfig.Default,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): ZIO[Scope, Throwable, ZKClient] =
    makeClient(sslConfig).map { client =>
      HttpClient.streaming(
        url,
        new ZIOStreamingBackend(client, ZIO.succeed(auth))
      )
    }

  /** Build exec-capable client from an already-constructed URL. */
  def makeWithExec(
      url: String,
      sslConfig: ClientSSLConfig = ClientSSLConfig.Default,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): ZIO[Scope, Throwable, ZKExecClient] =
    makeClient(sslConfig).map { client =>
      HttpClient.withExec(url, new ZIOExecBackend(client, ZIO.succeed(auth)))
    }

  /** Build client from a [[dev.hnaderi.k8s.client.Config]] data structure. */
  def fromConfig(
      config: dev.hnaderi.k8s.client.Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): ZIO[Scope, Throwable, ZKClient] =
    for {
      resolved <- resolveConfig(config, context, cluster)
      (clusterData, server, auth0) = resolved
      execResolved <- ZIOExec.resolve(auth0, clusterData)
      (auth, authenticator) = execResolved
      sslConfig <- ZIOSSLConfig.fromClusterAndAuth(clusterData, auth)
      client <- makeClient(sslConfig)
    } yield HttpClient.streaming(
      server,
      new ZIOStreamingBackend(client, authenticator)
    )

  /** Build exec-capable client from a [[dev.hnaderi.k8s.client.Config]] data
    * structure.
    */
  def fromConfigWithExec(
      config: dev.hnaderi.k8s.client.Config,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): ZIO[Scope, Throwable, ZKExecClient] =
    for {
      resolved <- resolveConfig(config, context, cluster)
      (clusterData, server, auth0) = resolved
      execResolved <- ZIOExec.resolve(auth0, clusterData)
      (auth, authenticator) = execResolved
      sslConfig <- ZIOSSLConfig.fromClusterAndAuth(clusterData, auth)
      client <- makeClient(sslConfig)
    } yield HttpClient.withExec(
      server,
      new ZIOExecBackend(client, authenticator)
    )

  /** Build client from explicit certificate file paths. */
  def from(
      server: String,
      ca: Option[String] = None,
      clientCert: Option[String] = None,
      clientKey: Option[String] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): ZIO[Scope, Throwable, ZKClient] =
    makeClient(ZIOSSLConfig.fromFiles(ca, clientCert, clientKey)).map {
      client =>
        HttpClient.streaming(
          server,
          new ZIOStreamingBackend(client, ZIO.succeed(auth))
        )
    }

  /** Build exec-capable client from explicit certificate file paths. */
  def fromWithExec(
      server: String,
      ca: Option[String] = None,
      clientCert: Option[String] = None,
      clientKey: Option[String] = None,
      auth: AuthenticationParams = AuthenticationParams.empty
  ): ZIO[Scope, Throwable, ZKExecClient] =
    makeClient(ZIOSSLConfig.fromFiles(ca, clientCert, clientKey)).map {
      client =>
        HttpClient.withExec(
          server,
          new ZIOExecBackend(client, ZIO.succeed(auth))
        )
    }

  /** Build client from a kubeconfig file. */
  def load(
      configFile: String,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): ZIO[Scope, Throwable, ZKClient] =
    readFile(configFile)
      .flatMap(content =>
        ZIO.fromEither(manifest.parse[dev.hnaderi.k8s.client.Config](content))
      )
      .flatMap(fromConfig(_, context, cluster))

  /** Build exec-capable client from a kubeconfig file. */
  def loadWithExec(
      configFile: String,
      context: Option[String] = None,
      cluster: Option[String] = None
  ): ZIO[Scope, Throwable, ZKExecClient] =
    readFile(configFile)
      .flatMap(content =>
        ZIO.fromEither(manifest.parse[dev.hnaderi.k8s.client.Config](content))
      )
      .flatMap(fromConfigWithExec(_, context, cluster))

  /** Build client using the kubeconfig found at the default location. Tries:
    *   - `KUBECONFIG` environment variable
    *   - `~/.kube/config`
    */
  def kubeconfig(
      context: Option[String] = None,
      cluster: Option[String] = None
  ): ZIO[Scope, Throwable, ZKClient] =
    kubeconfigPath.flatMap {
      case Some(path) => load(path, context, cluster)
      case None => ZIO.fail(new FileNotFoundException("No kubeconfig found"))
    }

  /** Build exec-capable client using the default kubeconfig location. */
  def kubeconfigWithExec(
      context: Option[String] = None,
      cluster: Option[String] = None
  ): ZIO[Scope, Throwable, ZKExecClient] =
    kubeconfigPath.flatMap {
      case Some(path) => loadWithExec(path, context, cluster)
      case None => ZIO.fail(new FileNotFoundException("No kubeconfig found"))
    }

  /** Build client from the pod's service account credentials at
    * `/var/run/secrets/kubernetes.io/serviceaccount`.
    */
  def podConfig: ZIO[Scope, Throwable, ZKClient] =
    podServiceAccountAuth.flatMap { auth =>
      from(
        server = podApiServer,
        ca = Some(s"$serviceAccountBase/ca.crt"),
        auth = auth
      )
    }

  /** Build exec-capable client from pod service account credentials. */
  def podConfigWithExec: ZIO[Scope, Throwable, ZKExecClient] =
    podServiceAccountAuth.flatMap { auth =>
      fromWithExec(
        server = podApiServer,
        ca = Some(s"$serviceAccountBase/ca.crt"),
        auth = auth
      )
    }

  /** Build client from the default config. Tries kubeconfig, then pod service
    * account.
    */
  def defaultConfig: ZIO[Scope, Throwable, ZKClient] =
    kubeconfig().orElse(podConfig)

  /** Build exec-capable client from the default config. Tries kubeconfig, then
    * pod service account.
    */
  def defaultConfigWithExec: ZIO[Scope, Throwable, ZKExecClient] =
    kubeconfigWithExec().orElse(podConfigWithExec)
}
