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
import cats.implicits._
import dev.hnaderi.k8s.jawn
import dev.hnaderi.k8s.utils._
import fs2.Stream
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.typelevel.jawn.Facade
import org.typelevel.jawn.fs2._

final case class Http4sKubernetesClient[F[_]: Concurrent, T](
    baseUrl: String,
    client: Client[F]
)(implicit
    enc: EntityEncoder[F, T],
    dec: EntityDecoder[F, T],
    builder: Builder[T],
    reader: Reader[T]
) extends HttpClient[F]
    with StreamingClient[Stream[F, *]] {
  private val dsl = new Http4sClientDsl[F] {}
  import dsl._

  private implicit lazy val jawnFacade: Facade.SimpleFacade[T] =
    jawn.jawnFacade[T]
  private implicit def encoder[A: Encoder]: EntityEncoder[F, A] =
    enc
      .contramap[A](_.encodeTo)

  private implicit def decoder[A: Decoder]: EntityDecoder[F, A] =
    dec.flatMapR(t =>
      t.decodeTo[A]
        .leftMap[DecodeFailure](InvalidMessageBodyFailure(_))
        .toEitherT
    )

  private def urlFrom(str: String, params: (String, String)*): F[Uri] =
    Concurrent[F]
      .fromEither(Uri.fromString(s"$baseUrl$str"))
      .map(_.withQueryParams(params.toMap))

  def get[O: Decoder](url: String, params: (String, String)*): F[O] = for {
    add <- urlFrom(url, params: _*)
    req = GET(add)
    res <- client.expect[O](req)
  } yield res

  def post[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O] = for {
    add <- urlFrom(url, params: _*)
    req = POST(body, add)
    res <- client.expect[O](req)
  } yield res

  def put[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O] = for {
    add <- urlFrom(url, params: _*)
    req = PUT(body, add)
    res <- client.expect[O](req)
  } yield res

  def patch[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O] = for {
    add <- urlFrom(url, params: _*)
    req = PATCH(body, add)
    res <- client.expect[O](req)
  } yield res

  def delete[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: Option[I] = None
  ): F[O] = for {
    add <- urlFrom(url, params: _*)
    req = body.fold(DELETE(add))(DELETE(_, add))
    res <- client.expect[O](req)
  } yield res

  def connect[O: Decoder](
      url: String,
      params: (String, String)*
  ): Stream[F, O] = {
    import Stream._

    eval(urlFrom(url).map(_.withQueryParam("watch")).map(GET(_)))
      .flatMap(client.stream)
      .flatMap(_.body.chunks.parseJsonStream[T])
      .flatMap { s =>
        s.decodeTo[O]
          .fold(err => raiseError[F](new Exception(s"$err\n$s")), emit(_))
      }
  }
}
