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

import com.dimafeng.testcontainers.K3sContainer
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.zio.ZIOKubernetesClient
import dev.hnaderi.k8s.client.zio.{ZKClient, ZKExecClient}
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.manifest
import io.k8s.api.core.v1.ConfigMap
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.Pod
import io.k8s.api.core.v1.PodSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import zio._
import zio.stream._
import zio.test._

object ZIOIntegrationSuite extends ZIOSpec[K3sContainer] {

  override val bootstrap: ZLayer[Any, Nothing, K3sContainer] = ZLayer.scoped {
    ZIO
      .acquireRelease(
        ZIO.attemptBlocking {
          val c = K3sContainer.Def().createContainer()
          c.start()
          c
        }
      )(c => ZIO.attemptBlocking(c.stop()).orDie)
      .orDie
  }

  private def withClient[A](
      f: ZKClient => ZIO[Scope, Throwable, A]
  ): ZIO[K3sContainer, Throwable, A] =
    ZIO.serviceWithZIO[K3sContainer] { container =>
      ZIO.scoped {
        for {
          config <- ZIO.fromEither(
            manifest.parse[dev.hnaderi.k8s.client.Config](container.kubeConfigYaml)
          )
          client <- ZIOKubernetesClient.fromConfig(config)
          result <- f(client)
        } yield result
      }
    }

  private def withExecClient[A](
      f: ZKExecClient => ZIO[Scope, Throwable, A]
  ): ZIO[K3sContainer, Throwable, A] =
    ZIO.serviceWithZIO[K3sContainer] { container =>
      ZIO.scoped {
        for {
          config <- ZIO.fromEither(
            manifest.parse[dev.hnaderi.k8s.client.Config](container.kubeConfigYaml)
          )
          client <- ZIOKubernetesClient.fromConfigWithExec(config)
          result <- f(client)
        } yield result
      }
    }

  private def waitForPhase(
      client: ZKExecClient,
      namespace: String,
      name: String,
      phase: String
  ): ZIO[Any, Throwable, Unit] = {
    def check: ZIO[Any, Throwable, Unit] =
      ZIO
        .scoped(APIs.namespace(namespace).pods.get(name).send(client))
        .flatMap { pod =>
          if (pod.status.flatMap(_.phase).contains(phase)) ZIO.unit
          else ZIO.sleep(java.time.Duration.ofSeconds(2)) *> check
        }
    check
  }

  private def podResource(
      client: ZKExecClient,
      pod: Pod,
      awaitPhase: String,
      namespace: String = "default"
  ): ZIO[Scope, Throwable, Pod] = {
    val podName = pod.metadata
      .flatMap(_.name)
      .getOrElse(throw new IllegalArgumentException("pod must have a name"))
    ZIO
      .acquireRelease(
        APIs.namespace(namespace).pods.create(pod).send(client)
      )(_ =>
        ZIO
          .scoped(APIs.namespace(namespace).pods.delete(podName).send(client).unit)
          .ignore
      )
      .flatMap { created =>
        val name = created.metadata.flatMap(_.name).getOrElse(podName)
        waitForPhase(client, namespace, name, awaitPhase).as(created)
      }
  }

  // ---- Pod definitions ----

  private def sleepingBusybox(name: String) = Pod(
    metadata = ObjectMeta(name = name),
    spec = PodSpec(
      containers = Seq(
        Container(
          name = "busybox",
          image = "busybox:1.36",
          command = Seq("sh", "-c", "sleep 3600")
        )
      ),
      restartPolicy = Some("Never")
    )
  )

  private def stdinBusybox(name: String) = Pod(
    metadata = ObjectMeta(name = name),
    spec = PodSpec(
      containers = Seq(
        Container(
          name = "busybox",
          image = "busybox:1.36",
          command = Seq("head", "-n", "1"),
          stdin = Some(true),
          stdinOnce = Some(true)
        )
      ),
      restartPolicy = Some("Never")
    )
  )

  private def logsPod(name: String) = Pod(
    metadata = ObjectMeta(name = name),
    spec = PodSpec(
      containers = Seq(
        Container(
          name = "busybox",
          image = "busybox:1.36",
          command = Seq("sh", "-c", "echo hello from logs; echo second line")
        )
      ),
      restartPolicy = Some("Never")
    )
  )

  // ---- Test suites ----

  private val namespaceSuite = suite("namespaces")(
    test("lists system namespaces") {
      withClient { client =>
        APIs.namespaces.list.send(client)
      }.map { result =>
        val names = result.items.flatMap(_.metadata.flatMap(_.name)).toSet
        assertTrue(names.contains("kube-system")) &&
        assertTrue(names.contains("default"))
      }
    }
  )

  private val configMapSuite = suite("configmaps")(
    test("create, get, and delete a ConfigMap") {
      val ns = "default"
      val cmName = "zio-integration-test-cm"
      val cm = ConfigMap(
        metadata = ObjectMeta(name = cmName),
        data = Map("key" -> "value")
      )
      withClient { client =>
        for {
          _ <- APIs.namespace(ns).configMaps.create(cm).send(client)
          fetched <- APIs.namespace(ns).configMaps.get(cmName).send(client)
          _ <- APIs.namespace(ns).configMaps.delete(cmName).send(client)
        } yield fetched
      }.map { fetched =>
        assertTrue(fetched.data == Some(Map("key" -> "value")))
      }
    }
  )

  private val podLogsSuite = suite("pod logs")(
    test("stream pod logs as lines") {
      val ns = "default"
      withExecClient { client =>
        podResource(client, logsPod("zio-logs-test-pod"), "Succeeded", ns).flatMap { pod =>
          val name = pod.metadata.flatMap(_.name).get
          client.lines(APIs.namespace(ns).pods.logs(name)).runCollect
        }
      }.map { lines =>
        assertTrue(lines.toList == List("hello from logs", "second line"))
      }
    }
  )

  private val podExecSuite = suite("pod exec")(
    test("exec echo into busybox pod and capture stdout") {
      val ns = "default"
      withExecClient { client =>
        podResource(client, sleepingBusybox("zio-exec-test-pod"), "Running", ns).flatMap {
          pod =>
            val name = pod.metadata.flatMap(_.name).get
            val pipe =
              client.pipe(APIs.namespace(ns).pods.exec(name, Seq("sh", "-c", "echo hello")))
            pipe(ZStream.empty).runCollect
        }
      }.map { events =>
        val stdout =
          events.collect { case ExecEvent.Stdout(data) => new String(data, "UTF-8") }.mkString
        assertTrue(stdout.trim == "hello")
      }
    },
    test("exec with stdin passes data to process") {
      val ns = "default"
      withExecClient { client =>
        podResource(
          client,
          sleepingBusybox("zio-exec-stdin-pod"),
          "Running",
          ns
        ).flatMap { pod =>
          val name = pod.metadata.flatMap(_.name).get
          val pipe = client.pipe(
            APIs.namespace(ns).pods.exec(name, Seq("sh", "-c", "head -n 1"), stdinEnabled = true)
          )
          val input = ZStream.succeed(ExecInput.Stdin("from stdin\n".getBytes("UTF-8")))
          pipe(input).runCollect
        }
      }.map { events =>
        val stdout =
          events.collect { case ExecEvent.Stdout(data) => new String(data, "UTF-8") }.mkString
        assertTrue(stdout.contains("from stdin"))
      }
    },
    test("attach with stdin passes data to running process") {
      val ns = "default"
      withExecClient { client =>
        podResource(client, stdinBusybox("zio-attach-pod"), "Running", ns).flatMap { pod =>
          val name = pod.metadata.flatMap(_.name).get
          val pipe = client.pipe(
            APIs.namespace(ns).pods.attach(name, stdinEnabled = true)
          )
          val input = ZStream.succeed(ExecInput.Stdin("from attach\n".getBytes("UTF-8")))
          pipe(input).runCollect
        }
      }.map { events =>
        val stdout =
          events.collect { case ExecEvent.Stdout(data) => new String(data, "UTF-8") }.mkString
        assertTrue(stdout.contains("from attach"))
      }
    }
  )

  def spec: Spec[K3sContainer with TestEnvironment with Scope, Any] =
    suite("ZIO Kubernetes Integration")(
      namespaceSuite,
      configMapSuite,
      podLogsSuite,
      podExecSuite
    ) @@ TestAspect.withLiveClock @@ TestAspect.sequential @@ TestAspect.timeout(
      java.time.Duration.ofMinutes(5)
    )
}
