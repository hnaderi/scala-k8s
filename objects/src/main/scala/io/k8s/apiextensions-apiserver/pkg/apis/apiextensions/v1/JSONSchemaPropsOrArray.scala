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

/* JSONSchemaPropsOrArray represents a value that can either be a JSONSchemaProps or an array of JSONSchemaProps. Mainly here for serialization purposes. */
sealed trait JSONSchemaPropsOrArray extends Any
object JSONSchemaPropsOrArray {
  final case class SingleValue(value: JSONSchemaProps)
      extends AnyVal
      with JSONSchemaPropsOrArray
  final case class MutipleValues(value: Seq[JSONSchemaProps])
      extends AnyVal
      with JSONSchemaPropsOrArray
  def apply(value: JSONSchemaProps): SingleValue = SingleValue(value)
  def apply(
      v1: JSONSchemaProps,
      v2: JSONSchemaProps,
      others: JSONSchemaProps*
  ): MutipleValues = MutipleValues(Seq(v1, v2) ++ others)
}
