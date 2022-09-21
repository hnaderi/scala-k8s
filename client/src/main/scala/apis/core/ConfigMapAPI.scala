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

object ConfigMapAPI
    extends NamespacedResourceAPI[ConfigMap, ConfigMapList](
      "/api/v1",
      "configmaps"
    )

final case class ConfigMapAPI(namespace: String)
    extends ConfigMapAPI.NamespacedAPIBuilders

object ClusterConfigMapAPI extends ConfigMapAPI.ClusterwideAPIBuilders
