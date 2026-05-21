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
import com.dimafeng.testcontainers.K3sContainer
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.http4s._
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.manifest
import fs2.Stream
import io.circe.Json
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.Pod
import io.k8s.api.core.v1.PodSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.CatsEffectSuite
import org.http4s.circe._

import scala.concurrent.duration._

class PodExecSuite extends CatsEffectSuite {

  override val munitIOTimeout: Duration = 5.minutes

  private val ns = "default"
  private val podName = "exec-test-pod"

  private def containerResource: Resource[IO, K3sContainer] =
    Resource.make(
      IO.blocking {
        val c = K3sContainer.Def().createContainer()
        c.start()
        c
      }
    )(c => IO.blocking(c.stop()))

  private val k3sExecClient = ResourceFunFixture(
      containerResource.flatMap { container =>
        Resource
          .eval(IO.fromEither(manifest.parse[Config](container.kubeConfigYaml)))
          .flatMap(JDKKubernetesClient[IO].fromConfigWithExec[Json](_))
      }
    )

  private def waitForRunning(client: KExecClient[IO], name: String): IO[Unit] = {
    def check: IO[Unit] = APIs
      .namespace(ns)
      .pods
      .get(name)
      .send(client)
      .flatMap { pod =>
        val phase = pod.status.flatMap(_.phase)
        if (phase.contains("Running")) IO.unit
        else IO.sleep(2.seconds) >> check
      }
    check
  }

  k3sExecClient.test("exec echo into busybox pod and capture stdout") { client =>
    val pod = Pod(
      metadata = ObjectMeta(name = podName),
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

    Resource
      .make(APIs.namespace(ns).pods.create(pod).send(client))(
        _ =>
          APIs
            .namespace(ns)
            .pods
            .delete(podName)
            .send(client)
            .void
            .handleError(_ => ())
      )
      .use { _ =>
        for {
          _ <- waitForRunning(client, podName)
          pipe = client.pipe(
            APIs.namespace(ns).pods.exec(podName, Seq("sh", "-c", "echo hello"))
          )
          events <- pipe(Stream.empty[IO]).compile.toList
          stdout = events
            .collect { case ExecEvent.Stdout(data) => new String(data, "UTF-8") }
            .mkString
        } yield assertEquals(stdout.trim, "hello")
      }
  }

  k3sExecClient.test("exec with stdin passes data to process") { client =>
    val pod = Pod(
      metadata = ObjectMeta(name = s"$podName-stdin"),
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

    Resource
      .make(APIs.namespace(ns).pods.create(pod).send(client))(
        _ =>
          APIs
            .namespace(ns)
            .pods
            .delete(s"$podName-stdin")
            .send(client)
            .void
            .handleError(_ => ())
      )
      .use { _ =>
        for {
          _ <- waitForRunning(client, s"$podName-stdin")
          pipe = client.pipe(
            APIs.namespace(ns).pods.exec(
              s"$podName-stdin",
              Seq("sh", "-c", "head -n 1"),
              stdinEnabled = true
            )
          )
          input = Stream.emit(ExecInput.Stdin("from stdin\n".getBytes("UTF-8")))
          events <- pipe(input).compile.toList
          stdout = events
            .collect { case ExecEvent.Stdout(data) => new String(data, "UTF-8") }
            .mkString
        } yield assert(stdout.contains("from stdin"))
      }
  }
}
