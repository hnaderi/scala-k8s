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

/** Kubernetes recommended labels as described in
  * https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/
  */
object Labels {

  /** The name of the application */
  def name(value: String): (String, String) = s"app.kubernetes.io/name" -> value

  /** A unique name identifying the instance of an application */
  def instance(value: String): (String, String) =
    s"app.kubernetes.io/instance" -> value

  /** The current version of the application (e.g., a semantic version, revision
    * hash, etc.)
    */
  def version(value: String): (String, String) =
    s"app.kubernetes.io/version" -> value

  /** The component within the architecture */
  def component(value: String): (String, String) =
    s"app.kubernetes.io/component" -> value

  /** The name of a higher level application this one is part of */
  def partOf(value: String): (String, String) =
    s"app.kubernetes.io/part-of" -> value

  /** The tool being used to manage the operation of an application */
  def managedBy(value: String): (String, String) =
    s"app.kubernetes.io/managed-by" -> value
}
