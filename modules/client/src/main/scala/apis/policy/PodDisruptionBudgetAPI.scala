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
package apis.policyv1

import io.k8s.api.policy.v1.PodDisruptionBudget
import io.k8s.api.policy.v1.PodDisruptionBudgetList

object PodDisruptionBudgetAPI
    extends PolicyV1.NamespacedResourceAPI[
      PodDisruptionBudget,
      PodDisruptionBudgetList
    ](
      "poddisruptionbudgets"
    )

final case class PodDisruptionBudgetAPI(namespace: String)
    extends PodDisruptionBudgetAPI.NamespacedAPIBuilders

object ClusterPodDisruptionBudgetAPI
    extends PodDisruptionBudgetAPI.ClusterwideAPIBuilders
