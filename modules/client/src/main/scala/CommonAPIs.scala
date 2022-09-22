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

import dev.hnaderi.k8s.utils._
import io.k8s.apimachinery.pkg.apis.meta.v1.APIResourceList
import io.k8s.apimachinery.pkg.apis.meta.v1.APIGroupList

abstract class ListingRequest[O: Decoder, COL: Decoder](
    url: String
) // TODO parameters
    extends HttpRequest[COL]
    with WatchRequest[WatchEvent[O]] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[COL] = http.get(url)
  override def listen[F[_]](http: StreamingClient[F]) =
    http.connect(url, "watch" -> "true")
}

abstract class GetRequest[O: Decoder](url: String)
    extends ListingRequest[O, O](url)

abstract class CreateRequest[RES: Encoder: Decoder](
    url: String,
    body: RES
) // TODO parameters
    extends HttpRequest[RES] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[RES] = http.post(url)(body)
}

abstract class PutRequest[IN: Encoder, OUT: Decoder](
    url: String,
    body: IN
) // TODO parameters
    extends HttpRequest[OUT] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.put(url)(body)
}

abstract class DeleteRequest[OUT: Decoder](url: String) // TODO parameters
    extends HttpRequest[OUT] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.delete(url)
}

abstract class APIResourceListingRequest(url: String)
    extends HttpRequest[APIResourceList] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[APIResourceList] = http.get(url)
}

abstract class APIGroupListingRequest(url: String)
    extends HttpRequest[APIGroupList] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[APIGroupList] = http.get(url)
}
