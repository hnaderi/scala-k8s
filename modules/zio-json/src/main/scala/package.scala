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
import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zio.json.ast.Json

package object zioJson {
  implicit val zioReader: Reader[Json] = ZIOReader
  implicit val zioBuilder: Builder[Json] = ZIOBuilder
  implicit def zioEncoderFor[T: Encoder]: JsonEncoder[T] =
    JsonEncoder[Json].contramap(_.encodeTo[Json])
  implicit def zioDecoderFor[T: Decoder]: JsonDecoder[T] =
    JsonDecoder[Json].mapOrFail(_.decodeTo[T])
}
