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

//> using dep "dev.hnaderi::scala-k8s-http4s-ember:0.11.1"
//> using dep "dev.hnaderi::scala-k8s-circe:0.11.1"
//> using dep "org.http4s::http4s-circe:0.23.19"

package example

import cats.effect._
import cats.effect.std.UUIDGen
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.http4s.EmberKubernetesClient
import dev.hnaderi.k8s.implicits._
import io.circe.Json
import io.k8s.api.core.v1.ConfigMap
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.circe._

object EmberExample extends IOApp {

  private val client = EmberKubernetesClient[IO].defaultConfig[Json]

  def operations(cl: HttpClient[IO]) = for {
    _ <- APIs.namespace("default").configmaps.list.send(cl).flatMap(IO.println)
    name <- UUIDGen[IO].randomUUID.map(_.toString)
    _ <- APIs
      .namespace("default")
      .configmaps
      .create(
        ConfigMap(
          metadata = ObjectMeta(name = name),
          data = Map("test" -> "value")
        )
      )
      .send(cl)
    a <- APIs.namespace("default").configmaps.get(name).send(cl)
    b <- APIs
      .namespace("default")
      .configmaps
      .replace(name, a.withData(Map("test2" -> "value2")))
      .send(cl)
    _ <- IO.println(b)
    _ <- APIs.namespace("default").configmaps.delete(name).send(cl)
  } yield ()

  override def run(args: List[String]): IO[ExitCode] = client
    .use(operations)
    .as(ExitCode.Success)
}
