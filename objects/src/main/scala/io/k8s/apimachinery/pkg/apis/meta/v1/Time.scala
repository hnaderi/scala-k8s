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

package io.k8s.apimachinery.pkg.apis.meta.v1

import dev.hnaderi.k8s.utils._

/** Time is a wrapper around time.Time which supports correct marshaling to YAML
  * and JSON. Wrappers are provided for many of the factory methods that the
  * time package offers.
  */
final case class Time(value: String) extends AnyVal
//TODO

object Time {
  implicit def encoder[T](implicit
      builder: Builder[T]
  ): Encoder[Time, T] = new Encoder[Time, T] {
    def apply(r: Time): T = builder.of(r.value)
  }
}
