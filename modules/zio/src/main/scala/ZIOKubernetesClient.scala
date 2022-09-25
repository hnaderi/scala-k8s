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
import org.typelevel.jawn.Facade
import org.typelevel.jawn.Parser
import zhttp.http
import zhttp.service.ChannelFactory
import zhttp.service.Client
import zhttp.service.EventLoopGroup
import zio._
import zio.stream.ZStream

import ZIOKubernetesClient._

final case class ZIOKubernetesClient[T: Reader: Builder](
    serverUrl: String,
    stringify: T => String
) extends HttpClient[Z]
    with StreamingClient[ZS] {

  private implicit val jawn: Facade.SimpleFacade[T] = jawnFacade[T]

  private def urlFor(
      url: String,
      params: Seq[(String, String)]
  ): Z[http.URL] = for {
    u <- ZIO.fromEither(http.URL.fromString(s"$serverUrl$url"))
    qp = params.foldLeft(Map.empty[String, List[String]]) { case (qs, (k, v)) =>
      qs.get(k) match {
        case None        => qs.updated(k, List(v))
        case Some(value) => qs.updated(k, v :: value)
      }
    }
  } yield u.setQueryParams(qp)

  private def expect[O: Decoder](req: http.Request): Z[O] = for {
    res <- Client.request(req, Client.Config.empty)
    t <- res.bodyAsByteArray
      .map(Parser.parseFromByteArray[T](_))
      .flatMap(ZIO.fromTry(_))
    o <- ZIO.fromEither(t.decodeTo[O].left.map(DecodeError(_)))
  } yield o

  override def get[O: Decoder](url: String, params: (String, String)*): Z[O] =
    for {
      u <- urlFor(url, params)
      req = http.Request(url = u)
      o <- expect(req)
    } yield o

  override def post[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): Z[O] =
    for {
      u <- urlFor(url, params)
      req = http.Request(
        method = http.Method.POST,
        url = u,
        data = http.HttpData.fromString(stringify(body.encodeTo[T]))
      )
      o <- expect(req)
    } yield o

  override def put[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): Z[O] = ???

  override def patch[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): Z[O] = ???

  override def delete[O: Decoder](
      url: String,
      params: (String, String)*
  ): Z[O] = ???

  override def connect[O: Decoder](
      url: String,
      params: (String, String)*
  ): ZS[O] = ???

}

object ZIOKubernetesClient {
  type Z[T] = ZIO[EventLoopGroup with ChannelFactory, Throwable, T]
  type ZS[T] = ZStream[EventLoopGroup with ChannelFactory, Throwable, T]

  final case class DecodeError(msg: String) extends Exception(msg)
}
