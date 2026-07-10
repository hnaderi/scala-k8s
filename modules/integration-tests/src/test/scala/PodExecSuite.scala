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
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.implicits._
import fs2.Stream
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.Pod
import io.k8s.api.core.v1.PodSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

class PodExecSuite extends K3sSuite {

  private val ns = "default"

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

  podFixture(sleepingBusybox("exec-test-pod"), "Running").test(
    "exec echo into busybox pod and capture stdout"
  ) { case (client, pod) =>
    val name = pod.metadata.flatMap(_.name).get
    val pipe = client.pipe(
      APIs.namespace(ns).pods.exec(name, Seq("sh", "-c", "echo hello"))
    )
    for {
      events <- pipe(Stream.empty[IO]).compile.toList
      stdout = events.collect { case ExecEvent.Stdout(data) =>
        new String(data, "UTF-8")
      }.mkString
    } yield assertEquals(stdout.trim, "hello")
  }

  podFixture(sleepingBusybox("exec-test-pod-stdin"), "Running").test(
    "exec with stdin passes data to process"
  ) { case (client, pod) =>
    val name = pod.metadata.flatMap(_.name).get
    val pipe = client.pipe(
      APIs
        .namespace(ns)
        .pods
        .exec(
          name,
          Seq("sh", "-c", "head -n 1"),
          stdinEnabled = true
        )
    )
    val input = Stream.emit(ExecInput.Stdin("from stdin\n".getBytes("UTF-8")))
    for {
      events <- pipe(input).compile.toList
      stdout = events.collect { case ExecEvent.Stdout(data) =>
        new String(data, "UTF-8")
      }.mkString
    } yield assert(stdout.contains("from stdin"))
  }

  podFixture(stdinBusybox("attach-test-pod"), "Running").test(
    "attach with stdin passes data to running process"
  ) { case (client, pod) =>
    val name = pod.metadata.flatMap(_.name).get
    val pipe = client.pipe(
      APIs.namespace(ns).pods.attach(name, stdinEnabled = true)
    )
    val input = Stream.emit(ExecInput.Stdin("from attach\n".getBytes("UTF-8")))
    for {
      events <- pipe(input).compile.toList
      stdout = events.collect { case ExecEvent.Stdout(data) =>
        new String(data, "UTF-8")
      }.mkString
    } yield assert(stdout.contains("from attach"))
  }
}
