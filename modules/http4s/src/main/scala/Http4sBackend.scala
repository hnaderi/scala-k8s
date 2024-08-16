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
package http4s

import cats.effect.Concurrent
import cats.syntax.all._
import dev.hnaderi.k8s.utils._
import fs2.Stream
import io.k8s.apimachinery.pkg.apis.meta.v1
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Cookie
import org.http4s.headers.`Content-Type`
import org.http4s.syntax.literals._
import org.http4s.{Request => HRequest}

final class Http4sBackend[F[_], T] private (client: Client[F])(implicit
    F: Concurrent[F],
    enc: EntityEncoder[F, T],
    dec: EntityDecoder[F, T],
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

  type Req = HRequest[F]

  private def cookiesFor(cookies: Seq[(String, String)]) = cookies
    .map { case (k, v) => RequestCookie(k, v) }
    .toList
    .toNel
    .map(Cookie(_))
    .fold(Headers.empty)(Headers(_))

  private def sendRequest[O: Decoder](req: HRequest[F]): F[O] = client
    .expectOr[T](req) { resp =>
      val err = resp.status match {
        case Status.Conflict     => ErrorStatus.Conflict
        case Status.NotFound     => ErrorStatus.NotFound
        case Status.Unauthorized => ErrorStatus.Unauthorized
        case Status.Forbidden    => ErrorStatus.Forbidden
        case Status.BadRequest   => ErrorStatus.BadRequest
        case e                   => ErrorStatus.Other(e.code)
      }
      resp.as[T].map(_.decodeTo[v1.Status]).flatMap {
        case Right(status) => F.raiseError(ErrorResponse(err, status))
        case Left(err)     => F.raiseError(new Exception(err))
      }
    }
    .flatMap(t =>
      F.fromEither(
        t.decodeTo[O]
          .leftMap[DecodeFailure](InvalidMessageBodyFailure(_))
      )
    )

  private def parseJsonStream: fs2.Pipe[F, Byte, T] = {
    import dev.hnaderi.k8s.jawn
    import org.typelevel.jawn._
    import fs2.{Pull, Chunk}

    implicit val jawnFacade: Facade.SimpleFacade[T] = jawn.jawnFacade[T]
    def go(
        parser: AsyncParser[T]
    )(s: Stream[F, Chunk[Byte]]): Pull[F, T, Unit] = {
      def handle(attempt: Either[ParseException, collection.Seq[T]]) =
        attempt.fold(Pull.raiseError[F], js => Pull.output(Chunk.from(js)))

      s.pull.uncons1.flatMap {
        case Some((a, stream)) =>
          handle(parser.absorb(a.toByteBuffer)) >> go(parser)(stream)
        case None =>
          handle(parser.finish()) >> Pull.done
      }
    }

    src => go(AsyncParser[T](AsyncParser.ValueStream))(src.chunks).stream
  }

  override def connect[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): Stream[F, O] = {
    import Stream._

    eval(urlFrom(url, params))
      .map(methodFor(verb)(_, Headers(headers) ++ cookiesFor(cookies)))
      .flatMap(client.stream(_))
      .flatMap(_.body.through(parseJsonStream))
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
    case APIVerb.GET      => Method.GET
    case APIVerb.POST     => Method.POST
    case APIVerb.DELETE   => Method.DELETE
    case APIVerb.PUT      => Method.PUT
    case APIVerb.PATCH(_) => Method.PATCH
  }

  private def contentType(verb: APIVerb) = verb match {
    case APIVerb.PATCH(patchType) => mediaTypeFor(patchType)
    case _                        => MediaType.application.json
  }

}

object Http4sBackend {
  def fromClient[F[_], T](
      client: Client[F]
  )(implicit
      F: Concurrent[F],
      enc: EntityEncoder[F, T],
      dec: EntityDecoder[F, T],
      builder: Builder[T],
      reader: Reader[T]
  ): Http4sBackend[F, T] = new Http4sBackend[F, T](client)
}
