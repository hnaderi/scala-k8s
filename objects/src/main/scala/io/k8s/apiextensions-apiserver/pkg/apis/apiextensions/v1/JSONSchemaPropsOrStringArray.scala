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

package io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1

import dev.hnaderi.k8s.utils._

/* JSONSchemaPropsOrStringArray represents a JSONSchemaProps or a string array. */
sealed trait JSONSchemaPropsOrStringArray extends Any
object JSONSchemaPropsOrStringArray {
  final case class PropsValue(value: JSONSchemaProps)
      extends AnyVal
      with JSONSchemaPropsOrStringArray
  final case class StringList(value: Seq[String])
      extends AnyVal
      with JSONSchemaPropsOrStringArray
  def apply(value: JSONSchemaProps): PropsValue = PropsValue(value)
  def apply(value: Seq[String]): StringList = StringList(value)
  def apply(value: String, others: String*): StringList = StringList(
    value +: others
  )

  implicit def encoder[T: Builder]: Encoder[JSONSchemaPropsOrStringArray, T] =
    new Encoder[JSONSchemaPropsOrStringArray, T] {
      def apply(r: JSONSchemaPropsOrStringArray): T = r match {
        case PropsValue(value) => value.encodeTo
        case StringList(value) => value.encodeTo
      }
    }
}
