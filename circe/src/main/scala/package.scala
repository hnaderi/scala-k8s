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

import dev.hnaderi.k8s.utils.Builder
import dev.hnaderi.k8s.utils.Decoder
import dev.hnaderi.k8s.utils.Encoder
import dev.hnaderi.k8s.utils.Reader
import io.circe.Json

package object circe {
  implicit val circeBuilder: Builder[Json] = CirceBuilder
  implicit val circeReader: Reader[Json] = CirceReader
  implicit def k8sEncoder[T](implicit
      enc: Encoder[T, Json]
  ): io.circe.Encoder[T] = o => enc(o)
  implicit def k8sDecoder[T](implicit
      dec: Decoder[Json, T]
  ): io.circe.Decoder[T] = io.circe.Decoder.decodeJson.emap(dec(_))
}
