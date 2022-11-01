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
import io.k8s.apimachinery.pkg.apis.meta.v1.Patch
import org.typelevel.jawn.Facade
import org.typelevel.jawn.Parser.parseFromByteArray
import sttp.client3._
import sttp.model.StatusCode
import sttp.model.Uri

import SttpKubernetesClient._

final class SttpKubernetesClient[F[_], T: Builder: Reader](
    serverUrl: String,
    client: SttpBackend[F, Any]
)(implicit serializer: BodySerializer[T])
    extends HttpClient[SttpF[F, *]] {

  private implicit val jawn: Facade.SimpleFacade[T] = jawnFacade[T]
  private val ra: ResponseAs[Either[Throwable, T], Any] =
    ResponseAsByteArray.mapWithMetadata { case (b, resp) =>
      resp.code match {
        case s if s.isSuccess        => parseFromByteArray[T](b).toEither
        case StatusCode.Conflict     => Left(ErrorResponse.Conflict)
        case StatusCode.Unauthorized => Left(ErrorResponse.Unauthorized)
        case StatusCode.NotFound     => Left(ErrorResponse.NotFound)
        case StatusCode.BadRequest   => Left(ErrorResponse.BadRequest)
        case other => Left(new Exception(s"General error $other"))
      }
    }

  private def respAs[O: Decoder]: ResponseAs[O, Any] =
    ra.map(_.flatMap(_.decodeTo[O].left.map(DecodeError(_)))).getRight

  private def urlFor(url: String, params: Seq[(String, String)]) =
    Uri
      .parse(s"$serverUrl$url")
      .fold(err => throw InvalidURL(err), identity)
      .addParams(params: _*)

  private implicit def bodyEncoder[O: Encoder]: BodySerializer[O] =
    serializer.compose(_.encodeTo)

  override def get[O: Decoder](
      url: String,
      params: (String, String)*
  ): F[Response[O]] =
    basicRequest
      .get(urlFor(url, params))
      .response(respAs[O])
      .send(client)

  override def post[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): F[Response[O]] =
    basicRequest
      .post(urlFor(url, params))
      .body(body)
      .response(respAs[O])
      .send(client)

  override def put[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): F[Response[O]] =
    basicRequest
      .put(urlFor(url, params))
      .body(body)
      .response(respAs[O])
      .send(client)

  override def patch[O: Decoder](
      url: String,
      params: (String, String)*
  )(body: Patch): F[Response[O]] =
    basicRequest
      .patch(urlFor(url, params))
      .body(body)
      .contentType(body.contentType)
      .response(respAs[O])
      .send(client)

  override def delete[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: Option[I]): F[Response[O]] = {
    val req = basicRequest
      .delete(urlFor(url, params))
      .response(respAs[O])

    body
      .fold(req)(req.body(_))
      .send(client)
  }

}

object SttpKubernetesClient {
  type SttpF[F[_], T] = F[Response[T]]
  final case class DecodeError(msg: String) extends Exception(msg)
  final case class InvalidURL(msg: String) extends Exception(msg)
}
