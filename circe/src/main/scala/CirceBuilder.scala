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
package circe

import dev.hnaderi.k8s.utils.Builder
import io.circe._
import io.circe.syntax._

private[circe] object CirceBuilder extends Builder[Json] {

  override def of(str: String): Json = str.asJson

  override def of(i: Int): Json = i.asJson

  override def of(l: Long): Json = l.asJson

  override def of(l: Double): Json = l.asJson

  override def of(b: Boolean): Json = b.asJson

  override def arr(a: Iterable[Json]): Json = Json.fromValues(a)

  override def obj(values: Iterable[(String, Json)]): Json =
    Json.fromFields(values)

  override def nil: Json = Json.Null
}
