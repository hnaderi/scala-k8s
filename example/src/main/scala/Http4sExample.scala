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
import fs2.Stream._
import io.circe.Json
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder

//NOTE run `kubectl proxy` before running this example
object Http4sExample extends IOApp {

  private val client =
    EmberClientBuilder
      .default[IO]
      .build
      .map(Http4sKubernetesClient[IO, Json]("http://localhost:8001", _))

  def watchNodes(cl: StreamingClient[fs2.Stream[IO, *]]) =
    CoreV1.nodes
      .list()
      .listen(cl)
      .flatMap(ev =>
        exec(
          IO.println(ev.event) >> IO.println(
            ev.payload.metadata.flatMap(_.name)
          )
        )
      )
      .compile
      .drain

  def printNodes(cl: HttpClient[IO]) =
    CoreV1.nodes
      .list()
      .send(cl)
      .flatMap(
        _.items.toList.map(_.metadata.flatMap(_.name)).traverse(IO.println)
      )

  def debug(cl: HttpClient[IO]) =
    APIs
      .namespace("kube-system")
      .configmaps
      .get("kube-proxy")
      .send(cl)
      .flatMap(IO.println)

  def debug2(cl: HttpClient[IO]) =
    CoreV1.resources
      .send(cl)
      .map(_.resources.map(_.name))
      .flatMap(IO.println)

  override def run(args: List[String]): IO[ExitCode] = client
    .use(debug2)
    .as(ExitCode.Success)
}
