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

/* JSONSchemaPropsOrBool represents JSONSchemaProps or a boolean value. Defaults to true for the boolean property. */
sealed trait JSONSchemaPropsOrBool extends Any
object JSONSchemaPropsOrBool {
  final case class PropsValue(value: JSONSchemaProps)
      extends AnyVal
      with JSONSchemaPropsOrBool
  final case class BoolValue(value: Boolean)
      extends AnyVal
      with JSONSchemaPropsOrBool
  def apply(value: JSONSchemaProps): PropsValue = PropsValue(value)
  def apply(value: Boolean): BoolValue = BoolValue(value)
}
