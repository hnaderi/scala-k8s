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

package test

import cats.effect._
import cats.implicits._
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.http4s.EmberKubernetesClient
import dev.hnaderi.k8s.client.implicits._
import dev.hnaderi.k8s.implicits._
import fs2.Stream._
import io.circe.Json
import io.k8s.api.core.v1.ConfigMap
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.circe._

object EmberExample extends IOApp {

  private val client = EmberKubernetesClient.defaultConfig[IO, Json]

  def operations(cl: HttpClient[IO]) = for {
    _ <- APIs.namespace("hnaderi").configmaps.list.send(cl).flatMap(IO.println)
    _ <- APIs
      .namespace("hnaderi")
      .configmaps
      .create(
        ConfigMap(
          metadata = ObjectMeta(name = "example"),
          data = Map("test" -> "value")
        )
      )
      .send(cl)
    a <- APIs.namespace("hnaderi").configmaps.get("example").send(cl)
    b <- APIs
      .namespace("hnaderi")
      .configmaps
      .replace("example", a.withData(Map("test2" -> "value2")))
      .send(cl)
    _ <- IO.println(b)
    _ <- APIs.namespace("hnaderi").configmaps.delete("example").send(cl)
  } yield ()

  override def run(args: List[String]): IO[ExitCode] = client
    .use(operations)
    .as(ExitCode.Success)
}
