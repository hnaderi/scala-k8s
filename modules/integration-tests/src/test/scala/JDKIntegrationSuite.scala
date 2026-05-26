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
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.jdk._
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.manifest
import io.circe.Json
import io.k8s.api.core.v1.ConfigMap
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.Pod
import io.k8s.api.core.v1.PodSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer

class JDKIntegrationSuite extends FunSuite {

  override val munitTimeout: scala.concurrent.duration.Duration =
    scala.concurrent.duration.Duration(5, "minutes")

  private val defaultNs = "default"

  private val container: Fixture[K3sContainer] = new Fixture[K3sContainer](
    "k3s-container"
  ) {
    private var c: K3sContainer = _
    def apply(): K3sContainer = c
    override def beforeAll(): Unit = {
      c = K3sContainer.Def().createContainer()
      c.start()
    }
    override def afterAll(): Unit =
      if (c != null) c.stop()
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(container)

  private def kubeConfig(): Config =
    manifest
      .parse[Config](container().kubeConfigYaml)
      .fold(throw _, identity)

  private def client(): JDKClient =
    JDKKubernetesClient.fromConfig[Json](kubeConfig())

  private def execClient(): JDKExecClient =
    JDKKubernetesClient.fromConfigWithExec[Json](kubeConfig())

  private def await[A](cf: CompletableFuture[A]): A =
    cf.get(1, TimeUnit.MINUTES)

  private final class Collector[A] extends Flow.Subscriber[A] {
    private val buf = ListBuffer.empty[A]
    private val done = new CompletableFuture[List[A]]()
    def result: CompletableFuture[List[A]] = done
    override def onSubscribe(s: Flow.Subscription): Unit =
      s.request(Long.MaxValue)
    override def onNext(a: A): Unit = { buf += a; () }
    override def onError(t: Throwable): Unit = { done.completeExceptionally(t); () }
    override def onComplete(): Unit = { done.complete(buf.toList); () }
  }

  private def collect[A](p: Flow.Publisher[A]): List[A] = {
    val c = new Collector[A]
    p.subscribe(c)
    c.result.get(2, TimeUnit.MINUTES)
  }

  private def waitForPhase(
      cli: JDKExecClient,
      namespace: String,
      name: String,
      phase: String
  ): Unit = {
    val deadline = System.currentTimeMillis() + 120000L
    while (true) {
      val pod =
        await(APIs.namespace(namespace).pods.get(name).send(cli))
      if (pod.status.flatMap(_.phase).contains(phase)) return
      if (System.currentTimeMillis() > deadline)
        fail(s"pod $name never reached phase $phase")
      Thread.sleep(2000)
    }
  }

  private def withPod[A](cli: JDKExecClient, pod: Pod, awaitPhase: String)(
      f: String => A
  ): A = {
    val name = pod.metadata.flatMap(_.name).getOrElse(
      throw new IllegalArgumentException("pod must have a name")
    )
    val created = await(
      APIs.namespace(defaultNs).pods.create(pod).send(cli)
    )
    val createdName = created.metadata.flatMap(_.name).getOrElse(name)
    try {
      waitForPhase(cli, defaultNs, createdName, awaitPhase)
      f(createdName)
    } finally {
      try await(APIs.namespace(defaultNs).pods.delete(createdName).send(cli))
      catch { case _: Throwable => () }
    }
  }

  private def collectStdout(events: List[ExecEvent]): String =
    events.collect { case ExecEvent.Stdout(d) => new String(d, "UTF-8") }.mkString

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

  test("lists system namespaces") {
    val res = await(APIs.namespaces.list.send(client()))
    val names = res.items.flatMap(_.metadata.flatMap(_.name)).toSet
    assert(names.contains("kube-system"))
    assert(names.contains("default"))
  }

  test("create, get, and delete a ConfigMap") {
    val cli = client()
    val cmName = "jdk-integration-test-cm"
    val cm = ConfigMap(
      metadata = ObjectMeta(name = cmName),
      data = Map("key" -> "value")
    )
    await(APIs.namespace(defaultNs).configMaps.create(cm).send(cli))
    val fetched =
      await(APIs.namespace(defaultNs).configMaps.get(cmName).send(cli))
    await(APIs.namespace(defaultNs).configMaps.delete(cmName).send(cli))
    assertEquals(fetched.data, Some(Map("key" -> "value")))
  }

  test("stream pod logs as lines") {
    val cli = execClient()
    val lines = withPod(cli, logsPod("jdk-logs-test-pod"), "Succeeded") { name =>
      collect(cli.lines(APIs.namespace(defaultNs).pods.logs(name)))
    }
    assertEquals(lines, List("hello from logs", "second line"))
  }

  test("exec echo into busybox pod and capture stdout") {
    val cli = execClient()
    val events = withPod(cli, sleepingBusybox("jdk-exec-test-pod"), "Running") {
      name =>
        val pipe = cli.pipe(
          APIs
            .namespace(defaultNs)
            .pods
            .exec(name, Seq("sh", "-c", "echo hello"))
        )
        collect(pipe(emptyPublisher))
    }
    assertEquals(collectStdout(events).trim, "hello")
  }

  test("exec with stdin passes data to process") {
    val cli = execClient()
    val events =
      withPod(cli, sleepingBusybox("jdk-exec-stdin-pod"), "Running") { name =>
        val pipe = cli.pipe(
          APIs
            .namespace(defaultNs)
            .pods
            .exec(
              name,
              Seq("sh", "-c", "head -n 1"),
              stdinEnabled = true
            )
        )
        collect(
          pipe(singletonPublisher(ExecInput.Stdin("from stdin\n".getBytes("UTF-8"))))
        )
      }
    assert(collectStdout(events).contains("from stdin"))
  }

  test("attach with stdin passes data to running process") {
    val cli = execClient()
    val events = withPod(cli, stdinBusybox("jdk-attach-pod"), "Running") {
      name =>
        val pipe = cli.pipe(
          APIs.namespace(defaultNs).pods.attach(name, stdinEnabled = true)
        )
        collect(
          pipe(singletonPublisher(ExecInput.Stdin("from attach\n".getBytes("UTF-8"))))
        )
    }
    assert(collectStdout(events).contains("from attach"))
  }

  private def emptyPublisher[A]: Flow.Publisher[A] = new Flow.Publisher[A] {
    override def subscribe(s: Flow.Subscriber[_ >: A]): Unit = {
      s.onSubscribe(new Flow.Subscription {
        override def request(n: Long): Unit = ()
        override def cancel(): Unit = ()
      })
      s.onComplete()
    }
  }

  private def singletonPublisher[A](a: A): Flow.Publisher[A] =
    new Flow.Publisher[A] {
      override def subscribe(s: Flow.Subscriber[_ >: A]): Unit = {
        s.onSubscribe(new Flow.Subscription {
          private var delivered = false
          override def request(n: Long): Unit =
            if (!delivered && n > 0) {
              delivered = true; s.onNext(a); s.onComplete()
            }
          override def cancel(): Unit = ()
        })
      }
    }
}
