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
import spray.json._

package object sprayJson {
  implicit val sprayJsonBuilder: Builder[JsValue] = SprayBuilder
  implicit val sprayJsonReader: Reader[JsValue] = SprayReader
  implicit def k8sJsonWriter[T](implicit enc: Encoder[T]): JsonWriter[T] =
    JsonWriter.func2Writer(enc(_))
  implicit def k8sJsonReader[T](implicit dec: Decoder[T]): JsonReader[T] =
    JsonReader.func2Reader(dec(_).fold(err => throw DecodeError(err), identity))
}
