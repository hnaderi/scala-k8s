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
import io.k8s.apimachinery.pkg.apis.meta.v1
import zio._
import zio.http._
import zio.json._

import ZIOBackend._
import ScopedZIO._

final case class ZIOBackend(
    client: Client
) extends HttpBackend[ScopedTask] {

  override def send[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): ScopedTask[O] = for {
    u <- urlFor(url, params)
    con <- contentType(verb)
    req = Request(
      method = methodFor(verb),
      url = u,
      body = Body.empty,
      headers = Headers(con) ++ cookiesFor(cookies),
      version = Version.`HTTP/1.1`,
      remoteAddress = None
    )
    o <- expect(req)
  } yield o

  override def send[I: Encoder, O: Decoder](
      url: String,
      verb: APIVerb,
      body: I,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): ScopedTask[O] = for {
    u <- urlFor(url, params)
    con <- contentType(verb)
    req = Request(
      method = methodFor(verb),
      url = u,
      body = Body.fromString(body.toJson),
      headers = Headers(con) ++ cookiesFor(cookies),
      version = Version.`HTTP/1.1`,
      remoteAddress = None
    )
    o <- expect(req)
  } yield o

  private def cookiesFor(values: Seq[(String, String)]) = NonEmptyChunk
    .fromIterableOption(values.map { case (k, v) =>
      Cookie.Request(k, v)
    })
    .map(Header.Cookie(_))
    .fold(Headers.empty)(Headers(_))

  private def methodFor: APIVerb => Method = {
    case APIVerb.GET      => Method.GET
    case APIVerb.POST     => Method.POST
    case APIVerb.DELETE   => Method.DELETE
    case APIVerb.PUT      => Method.PUT
    case APIVerb.PATCH(_) => Method.PATCH
  }

  private def contentType(verb: APIVerb) =
    ZIO.fromEither(
      Header.ContentType
        .parse(
          verb match {
            case APIVerb.PATCH(ptype) => ptype.contentType
            case _                    => "application/json"
          }
        )
        .left
        .map(new IllegalArgumentException(_))
    )

  private def urlFor(
      url: String,
      params: Seq[(String, String)]
  ): ScopedTask[http.URL] = for {
    u <- ZIO.fromEither(URL.decode(url))
    qp = params.foldLeft(Map.empty[String, Chunk[String]]) {
      case (qs, (k, v)) =>
        qs.get(k) match {
          case None        => qs.updated(k, Chunk(v))
          case Some(value) => qs.updated(k, Chunk(v) ++ value)
        }
    }
  } yield u.queryParams(qp)

  private def expect[O: Decoder](req: http.Request): ScopedTask[O] =
    client.request(req).flatMap { res =>
      def readBody[T: Decoder]: ScopedTask[T] =
        res.body.asString.flatMap(body =>
          ZIO.fromEither(
            JsonDecoder[T]
              .decodeJson(body)
              .left
              .map(DecodeError(_))
          )
        )

      if (res.status.isSuccess) readBody[O]
      else {
        val err = res.status match {
          case http.Status.Conflict     => ErrorStatus.Conflict
          case http.Status.BadRequest   => ErrorStatus.BadRequest
          case http.Status.Unauthorized => ErrorStatus.Unauthorized
          case http.Status.NotFound     => ErrorStatus.NotFound
          case s                        => ErrorStatus.Other(s.code)
        }
        val status = readBody[v1.Status]
        status.map(ErrorResponse(err, _)).flatMap(ZIO.die(_))
      }
    }
}

object ZIOBackend {
  final case class DecodeError(msg: String) extends Exception(msg)

  def make: ZLayer[Client, Nothing, ZIOBackend] = Scope.default >>> ZLayer {
    ZIO.service[Client].map(ZIOBackend(_))
  }
}
