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

/** Utility for filling data in `ConfigMap` or `Secret` */
object DataMap extends DataMapPlatform {
  private implicit class MapOps(m: Map[String, Data]) {
    def vMap[T](f: Data => T): Map[String, T] =
      m.map { case (k, v) => (k, f(v)) }.toMap
  }

  /** String data map, useful for `ConfigMap` data and `Secret` stringData
    * @note
    *   this is not a safe operation and might have side effects and throw
    *   exceptions too
    */
  def from(values: Map[String, Data]): Map[String, String] =
    values.vMap(_.getContent)

  /** String data map, useful for `ConfigMap` data and `Secret` stringData
    * @note
    *   this is not a safe operation and might have side effects and throw
    *   exceptions too
    */
  def apply(values: (String, Data)*): Map[String, String] =
    from(values.toMap)

  /** Binary base64 encoded data map, useful for `ConfigMap` binaryData and
    * `Secret` data
    * @note
    *   this is not a safe operation and might have side effects and throw
    *   exceptions too
    */
  def binaryFrom(values: Map[String, Data]): Map[String, String] =
    values.vMap(_.getBase64Content)

  /** Binary base64 encoded data map, useful for `ConfigMap` binaryData and
    * `Secret` data
    * @note
    *   this is not a safe operation and might have side effects and throw
    *   exceptions too
    */
  def binary(values: (String, Data)*): Map[String, String] =
    binaryFrom(values.toMap)
}
