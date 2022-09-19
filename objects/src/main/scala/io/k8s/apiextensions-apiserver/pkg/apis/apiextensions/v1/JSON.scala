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

/* JSON represents any valid JSON value. These types are supported: bool, int64, float64, string, []interface{}, map[string]interface{} and nil. */
final case class JSON(value: String) extends AnyVal
object JSON {
  implicit val encoder: Encoder[JSON] = new Encoder[JSON] {
    def apply[T](r: JSON)(implicit builder: Builder[T]): T = builder.of(r.value)
  }

  implicit val decoder: Decoder[JSON] =
    Decoder[String].map(JSON(_))
}
