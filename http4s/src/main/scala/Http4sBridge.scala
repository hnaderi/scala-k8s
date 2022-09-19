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
import dev.hnaderi.k8s.utils._
import fs2.Stream
import org.http4s.DecodeFailure
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._
import org.typelevel.jawn.Facade
import org.typelevel.jawn.fs2._

final class Http4sBridge[F[_]: Concurrent, T](client: Client[F])(implicit
    enc: EntityEncoder[F, T],
    dec: EntityDecoder[F, T],
    builder: Builder[T],
    reader: Reader[T]
) extends HttpClient[F]
    with StreamingClient[Stream[F, *]] {
  private val dsl = new Http4sClientDsl[F] {}
  import dsl._

  private implicit val jawnFacade: Facade.SimpleFacade[T] =
    new Facade.SimpleFacade[T] {
      override def jnull: T = builder.nil
      override def jfalse: T = builder.of(false)
      override def jtrue: T = builder.of(true)
      override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): T = {
        val n = BigDecimal(s.toString)
        if (n.isValidInt) builder.of(n.toIntExact)
        else if (n.isValidLong) builder.of(n.toLongExact)
        else builder.of(n.toDouble)
      }
      override def jstring(s: CharSequence): T = builder.of(s.toString)
      override def jarray(vs: List[T]): T = builder.arr(vs)
      override def jobject(vs: Map[String, T]): T = builder.obj(vs)
    }
  private implicit def encoder[A: Encoder]: EntityEncoder[F, A] =
    enc.contramap(_.encodeTo)
  private implicit def decoder[A: Decoder]: EntityDecoder[F, A] =
    dec.flatMapR(t =>
      t.decodeTo[A]
        .leftMap[DecodeFailure](InvalidMessageBodyFailure(_))
        .toEitherT
    )

  def get[O: Decoder](url: String, params: (String, String)*): F[O] = {
    val req = GET(uri"")

    client.expect(req)
  }
  def post[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O] = ???
  def put[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O] = ???
  def patch[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O] = ???
  def delete[O: Decoder](url: String, params: (String, String)*): F[O] = ???

  def connect[O: Decoder](
      url: String,
      params: (String, String)*
  ): Stream[F, O] = {
    val req = GET(uri"")

    client
      .stream(req)
      .flatMap(_.body.chunks.parseJsonStream[T])
      .map(_.decodeTo[O])
      .flatMap {
        case Right(o)  => Stream.emit(o)
        case Left(err) => Stream.raiseError[F](new Exception(err))
      }
  }
}

object Http4sBridge {}
