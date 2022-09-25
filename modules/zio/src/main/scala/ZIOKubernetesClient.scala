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
import zhttp.http
import zhttp.http.HttpData
import zhttp.http.Method
import zhttp.service.ChannelFactory
import zhttp.service.Client
import zhttp.service.EventLoopGroup
import zio._
import zio.json._

import ZIOKubernetesClient._

final case class ZIOKubernetesClient(
    serverUrl: String
) extends HttpClient[Z] {

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
    body <- res.bodyAsString
    o <- ZIO.fromEither(
      JsonDecoder[O]
        .decodeJson(body)
        .left
        .map(DecodeError(_))
    )
  } yield o

  private def send[I: Encoder, O: Decoder](
      url: String,
      params: Seq[(String, String)],
      method: http.Method,
      body: I
  ): Z[O] = for {
    u <- urlFor(url, params)
    req = http.Request(
      method = method,
      url = u,
      data = HttpData.fromString(body.toJson)
    )
    o <- expect(req)
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
    send(url, params, Method.POST, body)

  override def put[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): Z[O] =
    send(url, params, Method.PUT, body)

  override def patch[I: Encoder, O: Decoder](
      url: String,
      params: (String, String)*
  )(body: I): Z[O] =
    send(url, params, Method.PATCH, body)

  override def delete[O: Decoder](
      url: String,
      params: (String, String)*
  ): Z[O] =
    for {
      u <- urlFor(url, params)
      req = http.Request(url = u, method = Method.DELETE)
      o <- expect(req)
    } yield o
}

object ZIOKubernetesClient {
  type Z[T] = ZIO[EventLoopGroup with ChannelFactory, Throwable, T]

  final case class DecodeError(msg: String) extends Exception(msg)
}
