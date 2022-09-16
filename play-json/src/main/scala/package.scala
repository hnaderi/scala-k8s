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

import dev.hnaderi.k8s.utils._
import play.api.libs.json._

package object playJson {
  implicit val playJsonBuilder: Builder[JsValue] = PlayJsonBuilder
  implicit val playJsonReader: Reader[JsValue] = PlayJsonReader
  implicit def k8sJsonWrites[T](implicit enc: Encoder[T, JsValue]): Writes[T] =
    Writes(enc(_))
  implicit def k8sJsonReads[T](implicit dec: Decoder[JsValue, T]): Reads[T] =
    Reads(dec(_) match {
      case Right(value) => JsSuccess(value)
      case Left(err)    => JsError(err)
    })
}
