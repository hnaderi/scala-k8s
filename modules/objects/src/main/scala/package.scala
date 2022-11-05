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

import io.k8s.apimachinery.pkg.util.intstr.IntOrString
import io.k8s.apimachinery.pkg.util.intstr.IntOrString._

object implicits {
  implicit def convertToOption[T](t: T): Option[T] = Some(t)
  implicit def convertToIntValue(i: Int): IntOrString = IntValue(i)
  implicit def convertToStringValue(s: String): IntOrString = StringValue(s)
  implicit def convertToIntValueOpt(i: Int): Option[IntOrString] = Some(
    IntValue(i)
  )
  implicit def convertToStringValueOpt(s: String): Option[IntOrString] = Some(
    StringValue(s)
  )
}
