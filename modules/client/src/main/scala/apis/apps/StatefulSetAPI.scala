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
package apis.appsv1

import io.k8s.api.apps.v1.StatefulSet
import io.k8s.api.apps.v1.StatefulSetList

object StatefulSetAPI
    extends AppsV1.NamespacedResourceAPI[StatefulSet, StatefulSetList](
      "statefulsets"
    )

final case class StatefulSetAPI(namespace: String)
    extends StatefulSetAPI.NamespacedAPIBuilders

object ClusterStatefulSetAPI extends StatefulSetAPI.ClusterwideAPIBuilders
