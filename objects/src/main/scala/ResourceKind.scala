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

final case class ResourceKind(
    group: String,
    kind: String,
    version: String
)

trait KObject extends Serializable with Product {
  protected val _resourceKind: ResourceKind
  final def group: String = _resourceKind.group
  final lazy val kind: String = _resourceKind.kind
  final lazy val version: String = _resourceKind.version
  final lazy val apiVersion: String =
    if (group.isEmpty) version else s"$group/$version"

  def foldTo[T: utils.Builder]: T
}

object KObject {
  implicit def decoder[T: Reader]: Decoder[T, KObject] =
    ResourceCodecs.resourceDecoder
  implicit def encoder[T: Builder]: Encoder[KObject, T] =
    new Encoder[KObject, T] {
      def apply(r: KObject): T = r.foldTo[T]
    }
}
