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

import dev.hnaderi.k8s.jawn.jawnFacade
import dev.hnaderi.k8s.utils._
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import org.typelevel.jawn.Facade
import org.typelevel.jawn.Parser.parseFromByteArray
import sttp.client3._
import sttp.model.Header
import sttp.model.Method
import sttp.model.StatusCode
import sttp.model.Uri

import SttpKBackend._

final class SttpKBackend[F[_], T: Builder: Reader] private (
    client: SttpBackend[F, Any]
)(implicit serializer: BodySerializer[T])
    extends HttpBackend[SttpF[F, *]] {
  private implicit val jawn: Facade.SimpleFacade[T] = jawnFacade[T]
  private val ra: ResponseAs[Either[Throwable, T], Any] =
    ResponseAsByteArray.mapWithMetadata { case (b, resp) =>
      val body = parseFromByteArray[T](b).toEither
      if (resp.code.isSuccess) body
      else
        Left(
          body
            .flatMap(_.decodeTo[Status].left.map(DecodeError(_)))
            .map(
              ErrorResponse(
                resp.code match {
                  case StatusCode.Conflict     => ErrorStatus.Conflict
                  case StatusCode.Unauthorized => ErrorStatus.Unauthorized
                  case StatusCode.NotFound     => ErrorStatus.NotFound
                  case StatusCode.BadRequest   => ErrorStatus.BadRequest
                  case other                   => ErrorStatus.Other(other.code)
                },
                _
              )
            )
            .merge
        )
    }

  private def respAs[O: Decoder]: ResponseAs[O, Any] =
    ra.map(_.flatMap(_.decodeTo[O].left.map(DecodeError(_)))).getRight

  private def urlFor(url: String, params: Seq[(String, String)]) =
    Uri
      .parse(url)
      .fold(err => throw InvalidURL(err), identity)
      .addParams(params: _*)

  private def methodFor(verb: APIVerb) = verb match {
    case APIVerb.GET      => Method.GET
    case APIVerb.POST     => Method.POST
    case APIVerb.DELETE   => Method.DELETE
    case APIVerb.PUT      => Method.PUT
    case APIVerb.PATCH(_) => Method.PATCH
  }

  override def send[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): F[Response[O]] = basicRequest
    .method(methodFor(verb), urlFor(url, params))
    .headers(headers.map { case (k, v) => Header(k, v) }: _*)
    .cookies(cookies: _*)
    .response(respAs[O])
    .send(client)

  override def send[I: Encoder, O: Decoder](
      url: String,
      verb: APIVerb,
      body: I,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): F[Response[O]] = basicRequest
    .method(methodFor(verb), urlFor(url, params))
    .body(body.encodeTo[T])
    .headers(headers.map { case (k, v) => Header(k, v) }: _*)
    .cookies(cookies: _*)
    .response(respAs[O])
    .send(client)

}

object SttpKBackend {
  type SttpF[F[_], T] = F[Response[T]]
  final case class DecodeError(msg: String) extends Exception(msg)
  final case class InvalidURL(msg: String) extends Exception(msg)

  def apply[F[_], T: Builder: Reader: BodySerializer](
      client: SttpBackend[F, Any]
  ): SttpKBackend[F, T] = new SttpKBackend[F, T](client)

}
