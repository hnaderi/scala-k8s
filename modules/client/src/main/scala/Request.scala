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

package dev.hnaderi.k8s
package client

import dev.hnaderi.k8s.utils._

sealed trait Request
trait HttpRequest[O] extends Request {
  def send[F[_]](http: HttpClient[F]): F[O]
}
trait WatchRequest[O] extends Request {
  def listen[F[_]](http: StreamingClient[F]): F[O]
}

trait HttpClient[F[_]] {
  def get[O: Decoder](url: String, params: (String, String)*): F[O]
  def post[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O]
  def put[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: I
  ): F[O]
  def patch[I: Encoder, O: Decoder](
      url: String,
      patch: PatchType,
      params: (String, String)*
  )(
      body: I
  ): F[O]
  def delete[I: Encoder, O: Decoder](url: String, params: (String, String)*)(
      body: Option[I] = None
  ): F[O]

  final def send[O](req: HttpRequest[O]): F[O] = req.send(this)
}

object HttpClient {
  private abstract class Simple[F[_]](
      baseUri: String,
      backend: HttpBackend[F],
      auth: AuthenticationParams
  ) extends HttpClient[F] {
    override def get[O: Decoder](
        url: String,
        params: (String, String)*
    ): F[O] =
      backend.send(
        s"$baseUri$url",
        APIVerb.GET,
        params = params ++ auth.params,
        headers = auth.headers,
        cookies = auth.cookies
      )

    override def post[I: Encoder, O: Decoder](
        url: String,
        params: (String, String)*
    )(body: I): F[O] =
      backend.send(
        s"$baseUri$url",
        APIVerb.POST,
        body,
        params = params ++ auth.params,
        headers = auth.headers,
        cookies = auth.cookies
      )

    override def put[I: Encoder, O: Decoder](
        url: String,
        params: (String, String)*
    )(body: I): F[O] =
      backend.send(
        s"$baseUri$url",
        APIVerb.PUT,
        body,
        params = params ++ auth.params,
        headers = auth.headers,
        cookies = auth.cookies
      )

    override def patch[I: Encoder, O: Decoder](
        url: String,
        patch: PatchType,
        params: (String, String)*
    )(body: I): F[O] =
      backend.send(
        s"$baseUri$url",
        APIVerb.PATCH(patch),
        body,
        params = params ++ auth.params,
        headers = auth.headers,
        cookies = auth.cookies
      )

    override def delete[I: Encoder, O: Decoder](
        url: String,
        params: (String, String)*
    )(body: Option[I]): F[O] =
      backend.send(
        s"$baseUri$url",
        APIVerb.DELETE,
        params = params ++ auth.params,
        headers = auth.headers,
        cookies = auth.cookies
      )

  }

  def apply[F[_]](
      baseUri: String,
      backend: HttpBackend[F],
      auth: AuthenticationParams = AuthenticationParams()
  ): HttpClient[F] = new Simple[F](baseUri, backend, auth) {}

  def streaming[F[_], S[_]](
      baseUri: String,
      backend: HttpBackend[F] with StreamingBackend[S],
      auth: AuthenticationParams = AuthenticationParams()
  ): HttpClient[F] with StreamingClient[S] =
    new Simple[F](baseUri, backend, auth) with StreamingClient[S] {
      override def connect[O: Decoder](
          url: String,
          params: (String, String)*
      ): S[O] = backend.connect(
        s"$baseUri$url",
        APIVerb.GET,
        params = params ++ auth.params,
        headers = auth.headers,
        cookies = auth.cookies
      )
    }
}

trait StreamingClient[F[_]] {
  def connect[O: Decoder](url: String, params: (String, String)*): F[O]

  final def listen[O](req: WatchRequest[O]): F[O] = req.listen(this)
}

sealed trait APIVerb extends Serializable with Product

object APIVerb {
  case object GET extends APIVerb
  case object POST extends APIVerb
  case object PUT extends APIVerb
  case object DELETE extends APIVerb
  final case class PATCH(patchType: PatchType) extends APIVerb
}

trait HttpBackend[F[_]] {
  def send[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): F[O]

  def send[I: Encoder, O: Decoder](
      url: String,
      verb: APIVerb,
      body: I,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): F[O]
}

trait StreamingBackend[S[_]] {
  def connect[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): S[O]
}
