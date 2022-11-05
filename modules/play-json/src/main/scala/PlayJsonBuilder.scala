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

package dev.hnaderi.k8s.playJson

import play.api.libs.json._
import dev.hnaderi.k8s.utils.Builder

private[playJson] object PlayJsonBuilder extends Builder[JsValue] {

  override def of(str: String): JsValue = JsString(str)

  override def of(i: Int): JsValue = JsNumber(i)

  override def of(l: Long): JsValue = JsNumber(l)

  override def of(l: Double): JsValue = JsNumber(l)

  override def of(b: Boolean): JsValue = JsBoolean(b)

  override def arr(a: Iterable[JsValue]): JsValue = JsArray(a.toSeq)

  override def obj(values: Iterable[(String, JsValue)]): JsValue = JsObject(
    values.toMap
  )

  override def nil: JsValue = JsNull

}
