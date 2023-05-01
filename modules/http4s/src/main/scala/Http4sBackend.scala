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

import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.implicits._
import dev.hnaderi.k8s.utils._
import dev.hnaderi.k8s.utils._
import fs2.Stream
import org.http4s
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.`Content-Type`
import org.http4s.syntax.literals._

final case class Http4sBackend[F[_], T] private (client: Client[F])(implicit
    F: Concurrent[F],
    enc: http4s.EntityEncoder[F, T],
    dec: http4s.EntityDecoder[F, T],
    builder: Builder[T],
    reader: Reader[T]
) extends HttpBackend[F]
    with StreamingBackend[Stream[F, *]] {

  private val dsl = new Http4sClientDsl[F] {}
  import dsl._

  override def send[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): F[O] =
    urlFrom(url, params)
      .map { url =>
        methodFor(verb)(
          url,
          Headers(headers) ++ cookiesFor(cookies)
        ).withContentType(`Content-Type`(contentType(verb)))
      }
      .flatMap(sendRequest(_))

  override def send[I: Encoder, O: Decoder](
      url: String,
      verb: APIVerb,
      body: I,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): F[O] =
    urlFrom(url, params)
      .map { url =>
        methodFor(verb)(
          body.encodeTo[T],
          url,
          Headers(headers) ++ cookiesFor(cookies)
        ).withContentType(`Content-Type`(contentType(verb)))
      }
      .flatMap(sendRequest(_))

  type Req = http4s.Request[F]

  private def cookiesFor(cookies: Seq[(String, String)]) = cookies
    .map { case (k, v) => RequestCookie(k, v) }
    .toList
    .toNel
    .map(http4s.headers.Cookie(_))
    .fold(Headers.empty)(Headers(_))

  private def sendRequest[O: Decoder](req: http4s.Request[F]): F[O] = client
    .expectOr[T](req)(resp =>
      F.raiseError(resp.status match {
        case Status.Conflict     => ErrorResponse.Conflict
        case Status.NotFound     => ErrorResponse.NotFound
        case Status.Unauthorized => ErrorResponse.Unauthorized
        case Status.BadRequest   => ErrorResponse.BadRequest
        case e                   => new Exception(e.toString)
      })
    )
    .flatMap(t =>
      F.fromEither(
        t.decodeTo[O]
          .leftMap[DecodeFailure](InvalidMessageBodyFailure(_))
      )
    )

  override def connect[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Stream[F, O] = {
    import Stream._
    import org.typelevel.jawn.fs2._
    import dev.hnaderi.k8s.jawn
    import org.typelevel.jawn.Facade
    implicit val jawnFacade: Facade.SimpleFacade[T] = jawn.jawnFacade[T]

    eval(urlFrom(url, params))
      .map(methodFor(verb)(_, Headers(headers) ++ cookiesFor(cookies)))
      .flatMap(client.stream(_))
      .flatMap(_.body.chunks.parseJsonStream[T])
      .flatMap { s =>
        s.decodeTo[O]
          .fold(err => raiseError[F](new Exception(s"$err\n$s")), emit(_))
      }
  }

  private def urlFrom(str: String, params: Seq[(String, String)]): F[Uri] =
    Concurrent[F]
      .fromEither(Uri.fromString(str))
      .map(_.copy(query = Query(params.map { case (k, v) =>
        (k, Some(v))
      }: _*)))

  private def mediaTypeFor: PatchType => MediaType = {
    case PatchType.JsonPatch => MediaType.application.`json-patch+json`
    case PatchType.Merge     => MediaType.application.`merge-patch+json`
    case PatchType.StrategicMerge =>
      mediaType"application/strategic-merge-patch+json"
    case PatchType.ServerSide => mediaType"application/apply-patch+yaml"
  }

  private def methodFor(verb: APIVerb) = verb match {
    case APIVerb.POST             => Method.POST
    case APIVerb.PATCH(patchType) => Method.PATCH
    case APIVerb.GET              => Method.GET
    case APIVerb.DELETE           => Method.DELETE
    case APIVerb.PUT              => Method.PUT
  }

  private def contentType(verb: APIVerb) = verb match {
    case APIVerb.PATCH(patchType) => mediaTypeFor(patchType)
    case _                        => MediaType.application.json
  }

}

object Http4sBackend {
  type KClient[F[_]] = HttpBackend[F] with StreamingBackend[Stream[F, *]]

  def fromClient[F[_], T](
      client: Client[F]
  )(implicit
      F: Concurrent[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): KClient[F] = new Http4sBackend[F, T](client)

  def fromUrl[F[_], T](implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] =
    http4s.ember.client.EmberClientBuilder.default[F].build.map(fromClient(_))

  def from[F[_], T](
      config: Config,
      context: Option[String] = None
  )(implicit
      F: Async[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Resource[F, KClient[F]] = ???
}
