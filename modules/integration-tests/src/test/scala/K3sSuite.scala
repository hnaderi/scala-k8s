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

package dev.hnaderi.k8s.integration

import cats.effect.IO
import cats.effect.Resource
import cats.effect.SyncIO
import com.dimafeng.testcontainers.K3sContainer
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.http4s._
import dev.hnaderi.k8s.manifest
import io.circe.Json
import io.k8s.api.core.v1.Pod
import munit.CatsEffectSuite
import org.http4s.circe._

import java.nio.file.Files
import java.util.Base64
import scala.concurrent.duration._

trait K3sSuite extends CatsEffectSuite {

  override val munitIOTimeout: Duration = 5.minutes

  private def containerResource: Resource[IO, K3sContainer] =
    Resource.make(
      IO.blocking {
        val c = K3sContainer.Def().createContainer()
        c.start()
        c
      }
    )(c => IO.blocking(c.stop()))

  val k3sClient = ResourceFunFixture(
    containerResource.flatMap { container =>
      Resource
        .eval(IO.fromEither(manifest.parse[Config](container.kubeConfigYaml)))
        .flatMap(EmberKubernetesClient[IO].fromConfig[Json](_))
    }
  )

  val k3sExecClient = ResourceFunFixture(
    containerResource.flatMap { container =>
      Resource
        .eval(IO.fromEither(manifest.parse[Config](container.kubeConfigYaml)))
        .flatMap(JDKKubernetesClient[IO].fromConfigWithExec[Json](_))
    }
  )

  /** Rewrites a kubeconfig so the current user authenticates through an exec
    * credential plugin: a generated shell script that prints an `ExecCredential`
    * carrying the cluster's own client certificate/key (as raw PEM, per the
    * `client.authentication.k8s.io` protocol). Exercises exec -> mTLS -> API.
    */
  private def toExecPluginConfig(config: Config): IO[Config] = IO.blocking {
    val ctx = config.contexts
      .find(_.name == config.`current-context`)
      .getOrElse(throw new IllegalStateException("no current context"))
    val user = config.users
      .find(_.name == ctx.context.user)
      .getOrElse(throw new IllegalStateException("no user for context"))

    def decodePem(field: String, data: Option[String]): String =
      data
        .map(d => new String(Base64.getDecoder.decode(d), "UTF-8"))
        .getOrElse(throw new IllegalStateException(s"kubeconfig user has no $field"))

    val certPem = decodePem("client-certificate-data", user.user.`client-certificate-data`)
    val keyPem = decodePem("client-key-data", user.user.`client-key-data`)

    val response = Json
      .obj(
        "apiVersion" -> Json.fromString("client.authentication.k8s.io/v1beta1"),
        "kind" -> Json.fromString("ExecCredential"),
        "status" -> Json.obj(
          "clientCertificateData" -> Json.fromString(certPem),
          "clientKeyData" -> Json.fromString(keyPem)
        )
      )
      .noSpaces

    val respFile = Files.createTempFile("k8s-execcred-", ".json")
    Files.write(respFile, response.getBytes("UTF-8"))
    val script = Files.createTempFile("k8s-execplugin-", ".sh")
    Files.write(
      script,
      s"""|#!/bin/sh
          |cat "${respFile.toString}"
          |""".stripMargin.getBytes("UTF-8")
    )
    script.toFile.setExecutable(true)

    val execUser = NamedAuthInfo(
      user.name,
      AuthInfo(exec =
        Some(
          ExecConfig(
            apiVersion = "client.authentication.k8s.io/v1beta1",
            command = script.toString
          )
        )
      )
    )
    config.copy(users =
      config.users.map(u => if (u.name == user.name) execUser else u)
    )
  }

  val k3sExecPluginClient = ResourceFunFixture(
    containerResource.flatMap { container =>
      Resource
        .eval(IO.fromEither(manifest.parse[Config](container.kubeConfigYaml)))
        .evalMap(toExecPluginConfig)
        .flatMap(EmberKubernetesClient[IO].fromConfig[Json](_))
    }
  )

  def podFixture(
      pod: Pod,
      awaitPhase: String,
      namespace: String = "default"
  ): SyncIO[FunFixture[(KExecClient[IO], Pod)]] = {
    val podName = pod.metadata
      .flatMap(_.name)
      .getOrElse(
        throw new IllegalArgumentException("pod must have a name")
      )
    ResourceFunFixture(
      containerResource.flatMap { container =>
        Resource
          .eval(IO.fromEither(manifest.parse[Config](container.kubeConfigYaml)))
          .flatMap(JDKKubernetesClient[IO].fromConfigWithExec[Json](_))
          .flatMap { client =>
            Resource
              .make(APIs.namespace(namespace).pods.create(pod).send(client))(
                _ =>
                  APIs
                    .namespace(namespace)
                    .pods
                    .delete(podName)
                    .send(client)
                    .void
                    .handleError(_ => ())
              )
              .evalMap { created =>
                val name = created.metadata.flatMap(_.name).getOrElse(podName)
                waitForPhase(client, namespace, name, awaitPhase)
                  .as(client -> created)
              }
          }
      }
    )
  }

  def waitForPhase(
      client: KExecClient[IO],
      namespace: String,
      name: String,
      phase: String
  ): IO[Unit] = {
    def check: IO[Unit] = APIs
      .namespace(namespace)
      .pods
      .get(name)
      .send(client)
      .flatMap { pod =>
        if (pod.status.flatMap(_.phase).contains(phase)) IO.unit
        else IO.sleep(2.seconds) >> check
      }
    check
  }
}
