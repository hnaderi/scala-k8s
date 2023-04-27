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

import apis.corev1._

trait CoreV1Namespaced { self: NamespacedAPI =>
  final val configmaps: ConfigMapAPI = ConfigMapAPI(namespace)
  final val secrets: SecretAPI = SecretAPI(namespace)
  final val services: ServiceAPI = ServiceAPI(namespace)
  final val pods: PodAPI = PodAPI(namespace)

  final val limitRanges: LimitRangeAPI = LimitRangeAPI(namespace)
  final val events: EventAPI = EventAPI(namespace)
  final val serviceAccounts: ServiceAccountAPI = ServiceAccountAPI(namespace)
  final val resourceQuotas: ResourceQuotaAPI = ResourceQuotaAPI(namespace)

}
