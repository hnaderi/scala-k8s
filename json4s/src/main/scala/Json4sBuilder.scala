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

package dev.hnaderi.k8s.json4s

import dev.hnaderi.k8s.utils.Builder
import org.json4s._

object Json4sBuilder extends Builder[JValue] {

  override def of(str: String): JValue = JString(str)

  override def of(i: Int): JValue = JInt(i)

  override def of(l: Long): JValue = JLong(l)

  override def of(l: Double): JValue = JDouble(l)

  override def of(b: Boolean): JValue = JBool(b)

  override def arr(a: Iterable[JValue]): JValue = JArray(a.toList)

  override def obj(values: Iterable[(String, JValue)]): JValue = JObject(
    values.toList
  )

  override def nil: JValue = JNull

}
