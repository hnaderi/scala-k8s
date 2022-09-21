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

trait CoreV1Namespaced {
  protected def namespace: String

  def configmap(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val configmaps: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

  def secrets(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val secrets: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

  def services(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val services: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

  def pods(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val pods: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

  def endpoints(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val endpoints: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

  def limitRanges(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val limitRanges: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

  def events(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val events: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

  def serviceAccounts(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val serviceAccounts: ConfigMapAPINamespaced = ConfigMapAPINamespaced(
    namespace
  )

  def resourceQuotas(name: String): ConfigMapAPIExact =
    ConfigMapAPIExact(namespace, name)
  val resourceQuotas: ConfigMapAPINamespaced = ConfigMapAPINamespaced(namespace)

}
