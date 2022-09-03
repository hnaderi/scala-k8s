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

package dev.hnaderi.k8s.generator

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class Definition(
    description: Option[String],
    required: Option[Seq[String]],
    properties: Option[Map[String, Property]],
    `type`: Option[String],
    `x-kubernetes-group-version-kind`: Option[Seq[Kind]]
)
object Definition {
  implicit val codec: Codec[Definition] = deriveCodec
}

final case class Property(
    description: Option[String],
    format: Option[String],
    `type`: Option[String],
    items: Option[Property],
    additionalProperties: Option[Property],
    $ref: Option[String]
)
object Property {
  implicit val codec: Codec[Property] = deriveCodec
}

final case class Kind(
    group: String,
    kind: String,
    version: String
)
object Kind {
  implicit val codec: Codec[Kind] = deriveCodec
}
