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
import io.k8s.api.core.v1.ConfigMap
import io.k8s.api.core.v1.ConfigMapList

final case class ExactConfigMapAPI(ns: String, name: String) {
  import ExactConfigMapAPI._
  def get: Get = Get(ns, name)
}
object ExactConfigMapAPI {
  final case class Get(namespace: String, name: String)
      extends ListingRequest[ConfigMap](
        s"/api/v1/namespaces/$namespace/configmaps/$name"
      )
}
final case class NamespacedConfigMapAPI(ns: String) {
  import NamespacedConfigMapAPI._
  def create(cm: ConfigMap): Create = Create(ns, cm)
}
object NamespacedConfigMapAPI {
  final case class Create(namespace: String, configmap: ConfigMap)
      extends HttpRequest[ConfigMap] {
    override def run[F[_]](
        http: HttpClient[F]
    ): F[ConfigMap] =
      http.post(s"/api/v1/namespaces/${namespace}/configmaps")(configmap)
  }
}

object ClusterConfigMapAPI {
  final case class List()
      extends ListingRequest[ConfigMapList]("/api/v1/configmaps")
  def list: List = List()
}

abstract case class NamespacedResourceAPI(
    namespace: String,
    group: String,
    version: String,
    resourceType: String
) {
  import NamespacedResourceAPI._

  private val baseUrl = s"""${urlForGroup(
      group
    )}/namespaces/$namespace/$resourceType"""
}

abstract class ListingRequest[COL: Decoder](url: String) // TODO parameters
    extends HttpRequest[COL]
    with WatchRequest[WatchEvent[COL]] {
  override def run[F[_]](
      http: HttpClient[F]
  ): F[COL] = http.get(url)
  override def start[F[_]](http: StreamingClient[F]) =
    http.connect(url, "watch" -> "true")
}

abstract class CreateRequest[IN: Encoder, OUT: Decoder](
    url: String,
    body: IN
) // TODO parameters
    extends HttpRequest[OUT] {
  override def run[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.post(url)(body)
}

abstract class PutRequest[IN: Encoder, OUT: Decoder](
    url: String,
    body: IN
) // TODO parameters
    extends HttpRequest[OUT] {
  override def run[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.put(url)(body)
}

abstract class DeleteRequest[OUT: Decoder](url: String) // TODO parameters
    extends HttpRequest[OUT] {
  override def run[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.delete(url)
}

object NamespacedResourceAPI {
  private def urlForGroup(group: String) = if (group == "") "/api" else "/apis"
}
