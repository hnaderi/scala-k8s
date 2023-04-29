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

package dev.hnaderi.k8s.client

import dev.hnaderi.k8s.utils.Decoder
import dev.hnaderi.k8s.utils.Encoder
import dev.hnaderi.k8s.zioJson._
import zio._
import zio.http._
import zio.json._

import ZIOKubernetesClient._

final case class ZIOKubernetesClient(
    serverUrl: String,
    client: Client
) extends HttpClient[Task] {

  private def urlFor(
      url: String,
      params: Seq[(String, String)]
  ): Task[http.URL] = for {
    u <- ZIO.fromEither(URL.decode(s"$serverUrl$url"))
    qp = params.foldLeft(Map.empty[String, Chunk[String]]) {
      case (qs, (k, v)) =>
        qs.get(k) match {
          case None        => qs.updated(k, Chunk(v))
          case Some(value) => qs.updated(k, Chunk(v) ++ value)
        }
    }
  } yield u.withQueryParams(qp)

  private def expect[O: Decoder](req: http.Request): Task[O] =
    client.request(req).flatMap { res =>
      def readBody: Task[O] = res.body.asString.flatMap(body =>
        ZIO.fromEither(
          JsonDecoder[O]
            .decodeJson(body)
            .left
            .map(DecodeError(_))
        )
      )

      res.status match {
        case s if s.isSuccess         => readBody
        case http.Status.Conflict     => ZIO.die(ErrorResponse.Conflict)
        case http.Status.BadRequest   => ZIO.die(ErrorResponse.BadRequest)
        case http.Status.Unauthorized => ZIO.die(ErrorResponse.Unauthorized)
        case http.Status.NotFound     => ZIO.die(ErrorResponse.NotFound)
        case s => ZIO.die(new Exception(s"General error $s"))
      }
    }

  private def send[I: Encoder, O: Decoder](
      url: String,
      params: Seq[(String, String)],
      method: http.Method,
      body: Option[I],
      contentType: String = "application/json"
  ): Task[O] = for {
    u <- urlFor(url, params)
    con <- ZIO.fromEither(
      Header.ContentType.parse(contentType).left.map(new Exception(_))
    )
    req = Request(
      method = method,
      url = u,
      body = body.fold(Body.empty)(b => Body.fromString(b.toJson)),
      headers = Headers(con),
      version = Version.`HTTP/1.1`,
      remoteAddress = None
    )
    o <- expect(req)
  } yield o

  override def get[O: Decoder](
      url: String,
      params: (String, String)*
  ): Task[O] =
    for {
      u <- urlFor(url, params)
      req = Request.get(u)
      o <- expect(req)
    } yield o

  override def post[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): Task[O] =
    send(url, params, Method.POST, Some(body))

  override def put[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): Task[O] =
    send(url, params, Method.PUT, Some(body))

  override def patch[I: Encoder, O: Decoder](
      url: String,
      patch: PatchType,
      params: (String, String)*
  )(
      body: I
  ): Task[O] =
    send(url, params, Method.PATCH, Some(body), contentType = patch.contentType)

  override def delete[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: Option[I]): Task[O] =
    send(url, params, Method.DELETE, body)
}

object ZIOKubernetesClient {
  final case class DecodeError(msg: String) extends Exception(msg)

  def send[O](
      req: HttpRequest[O]
  ): ZIO[ZIOKubernetesClient, Throwable, O] =
    ZIO.service[ZIOKubernetesClient].flatMap(req.send)

  def make(url: String): ZLayer[Any, Throwable, ZIOKubernetesClient] =
    Client.default.map(zc => ZEnvironment(ZIOKubernetesClient(url, zc.get)))
}
