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

import cats.data.EitherT
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.CoreV1
import dev.hnaderi.k8s.client.Http4sKubernetesClient
import dev.hnaderi.k8s.client.Http4sKubernetesClient.jawnFacade
import dev.hnaderi.k8s.json4s._
import fs2.Stream._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.json4s.JValue
import org.json4s.native.JsonMethods._
import org.typelevel.jawn.fs2._

object Main extends IOApp {
  private implicit val enc: EntityEncoder[IO, JValue] =
    EntityEncoder[IO, String].contramap[JValue](j => compact(render(j)))
  private implicit val dec: EntityDecoder[IO, JValue] =
    EntityDecoder.decodeBy(MediaType.application.json)(m =>
      EitherT(
        m.body.chunks
          .parseJsonStream[JValue]
          .compile
          .last
          .map(_.toRight(InvalidMessageBodyFailure("")))
      )
    )

  private val client =
    EmberClientBuilder
      .default[IO]
      .build
      .map(Http4sKubernetesClient[IO, JValue]("http://localhost:8001", _))

  def watchNodes(cl: Http4sKubernetesClient[IO, JValue]) =
    CoreV1.nodes.list
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

  def printNodes(cl: Http4sKubernetesClient[IO, JValue]) =
    CoreV1.nodes.list
      .send(cl)
      .flatMap(
        _.items.toList.map(_.metadata.flatMap(_.name)).traverse(IO.println)
      )

  def debug(cl: Http4sKubernetesClient[IO, JValue]) =
    APIs
      .namespace("kube-system")
      .configmaps
      .get("kube-proxy")
      .send(cl)
      .flatMap(IO.println)

  def debug2(cl: Http4sKubernetesClient[IO, JValue]) =
    APIs.nodes.list
      .send(cl)
      .flatMap(IO.println)

  override def run(args: List[String]): IO[ExitCode] = client
    .use(debug2)
    .as(ExitCode.Success)
}
