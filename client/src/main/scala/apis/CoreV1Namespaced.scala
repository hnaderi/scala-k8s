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

trait CoreV1Namespaced { self: NamespacedAPI =>
  val configmaps: ConfigMapAPI = ConfigMapAPI(namespace)
  val secrets: SecretAPI = SecretAPI(namespace)
  val services: ServiceAPI = ServiceAPI(namespace)
  val pods: PodAPI = PodAPI(namespace)

  val limitRanges: LimitRangeAPI = LimitRangeAPI(namespace)
  val events: EventAPI = EventAPI(namespace)
  val serviceAccounts: ServiceAccountAPI = ServiceAccountAPI(namespace)
  val resourceQuotas: ResourceQuotaAPI = ResourceQuotaAPI(namespace)

}
