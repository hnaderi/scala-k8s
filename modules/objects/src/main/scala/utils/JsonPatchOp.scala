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

package dev.hnaderi.k8s.utils

import KSON._

/** implementation of Json patch spec from
  * [rfc6902](https://www.rfc-editor.org/rfc/rfc6902)
  */
sealed trait JsonPatchOp extends Serializable with Product
object JsonPatchOp {
  final case class Add(path: String, value: KSON) extends JsonPatchOp
  final case class Remove(path: String) extends JsonPatchOp
  final case class Replace(path: String, value: KSON) extends JsonPatchOp
  final case class Test(path: String, value: KSON) extends JsonPatchOp
  final case class Move(from: String, path: String) extends JsonPatchOp
  final case class Copy(from: String, path: String) extends JsonPatchOp

  implicit val encoder: Encoder[JsonPatchOp] =
    Encoder.mapBuilder[KSON].contramap {
      case Add(path, value) =>
        Map(
          "op" -> KString("add"),
          "path" -> KString(path),
          "value" -> value
        )
      case Remove(path) =>
        Map(
          "op" -> KString("remove"),
          "path" -> KString(path)
        )
      case Replace(path, value) =>
        Map(
          "op" -> KString("replace"),
          "path" -> KString(path),
          "value" -> value
        )
      case Test(path, value) =>
        Map(
          "op" -> KString("test"),
          "path" -> KString(path),
          "value" -> value
        )
      case Move(from, path) =>
        Map(
          "op" -> KString("move"),
          "from" -> KString(from),
          "path" -> KString(path)
        )
      case Copy(from, path) =>
        Map(
          "op" -> KString("copy"),
          "from" -> KString(from),
          "path" -> KString(path)
        )
    }
}
