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

import io.k8s.api.core.v1.ConfigMap
import io.k8s.api.core.v1.ConfigMapList

import ConfigMapAPI._

final case class ConfigMapAPIExact(ns: String, name: String) {
  def get: Get = Get(ns, name)
}
final case class ConfigMapAPINamespaced(ns: String) {
  def create(cm: ConfigMap): Create = Create(ns, cm)
}
object ClusterConfigMapAPI {
  final case class List()
      extends ListingRequest[ConfigMap, ConfigMapList]("/api/v1/configmaps")

  val list: List = List()
}

object ConfigMapAPI {
  final case class Create(namespace: String, configmap: ConfigMap)
      extends CreateRequest(
        s"/api/v1/namespaces/${namespace}/configmaps",
        configmap
      )
  final case class Get(namespace: String, name: String)
      extends GetRequest[ConfigMap](
        s"/api/v1/namespaces/$namespace/configmaps/$name"
      )
}
