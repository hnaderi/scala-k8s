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

package example

import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.jdk._
import dev.hnaderi.k8s.implicits._
import io.circe.Json
import io.k8s.api.core.v1.ConfigMap
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object JDKExample {

  def main(args: Array[String]): Unit = {
    completableFutureExample()
    futureExample()
    watchExample()
  }

  // ---- CompletableFuture (JDK-native) ----

  def completableFutureExample(): Unit = {
    val client: JDKClient = JDKKubernetesClient.defaultConfig[Json]

    val cm = ConfigMap(
      metadata = ObjectMeta(name = "jdk-example"),
      data = Map("hello" -> "world")
    )

    val cf: CompletableFuture[ConfigMap] = APIs
      .namespace("default")
      .configMaps
      .create(cm)
      .send(client)
      .thenCompose { created =>
        APIs
          .namespace("default")
          .configMaps
          .get(created.metadata.flatMap(_.name).getOrElse("jdk-example"))
          .send(client)
      }
      .whenComplete { (got, _) =>
        if (got != null) println(s"created configmap: ${got.data}")
        APIs
          .namespace("default")
          .configMaps
          .delete("jdk-example")
          .send(client)
      }

    cf.get(30, java.util.concurrent.TimeUnit.SECONDS)
    ()
  }

  // ---- scala.concurrent.Future (plain Scala) ----

  def futureExample(): Unit = {
    val client: FutureClient = FutureKubernetesClient.defaultConfig[Json]

    val program: Future[Unit] = for {
      list <- APIs.namespaces.list.send(client)
      names = list.items.flatMap(_.metadata.flatMap(_.name))
      _ = names.foreach(println)
    } yield ()

    Await.result(program, 30.seconds)
  }

  // ---- Streaming a watch via Flow.Publisher ----

  def watchExample(): Unit = {
    val client: JDKClient = JDKKubernetesClient.defaultConfig[Json]

    // Print the first 5 events on the namespaces watch, then exit.
    val publisher: Flow.Publisher[WatchEvent[
      io.k8s.api.core.v1.Namespace
    ]] = client.listen(APIs.namespaces.list)

    val done = new CompletableFuture[Unit]()
    val seen = ListBuffer.empty[String]
    publisher.subscribe(
      new Flow.Subscriber[WatchEvent[
        io.k8s.api.core.v1.Namespace
      ]] {
        private var sub: Flow.Subscription = _
        override def onSubscribe(s: Flow.Subscription): Unit = {
          sub = s; s.request(5L)
        }
        override def onNext(
            ev: WatchEvent[io.k8s.api.core.v1.Namespace]
        ): Unit = {
          seen += s"${ev.event}: ${ev.payload.metadata.flatMap(_.name)}"
          if (seen.size >= 5) sub.cancel()
          ()
        }
        override def onError(t: Throwable): Unit = {
          done.completeExceptionally(t); ()
        }
        override def onComplete(): Unit = { done.complete(()); () }
      }
    )

    try done.get(30, java.util.concurrent.TimeUnit.SECONDS)
    catch { case _: Throwable => () }
    seen.foreach(println)
  }
}
