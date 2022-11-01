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

package io.k8s.apimachinery.pkg.apis.meta.v1

import dev.hnaderi.k8s.utils.Builder
import dev.hnaderi.k8s.utils.Encoder
import dev.hnaderi.k8s.utils.EncoderOps
import dev.hnaderi.k8s.utils.JsonPatchOp
import dev.hnaderi.k8s.utils.KSON

/** Patch is provided to give a concrete name and type to the Kubernetes PATCH
  * request body.
  */
sealed trait Patch extends Serializable with Product {
  def foldTo[T: Builder]: T
  val contentType: String
}
object Patch {
  final case class JsonPatch(operations: List[JsonPatchOp] = Nil)
      extends Patch {
    def foldTo[T: Builder]: T = Builder[T].arr(operations.map(_.encodeTo))
    val contentType: String = "application/json-patch+json"

    def append(op: JsonPatchOp*): JsonPatch = JsonPatch(operations ++ op)

    def add[T: Encoder](path: String, value: T) = append(
      JsonPatchOp.Add(path, value.encodeTo[KSON])
    )
    def remove(path: String) = append(JsonPatchOp.Remove(path))
    def replace[T: Encoder](path: String, value: T) = append(
      JsonPatchOp.Replace(path, value.encodeTo[KSON])
    )
    def test[T: Encoder](path: String, value: T) = append(
      JsonPatchOp.Test(path, value.encodeTo[KSON])
    )
    def move(from: String, path: String) = append(JsonPatchOp.Move(from, path))
    def copy(from: String, path: String) = append(JsonPatchOp.Copy(from, path))
  }

  implicit val encoder: Encoder[Patch] = new Encoder[Patch] {
    def apply[T: Builder](r: Patch): T = r.foldTo[T]
  }

  final case class JsonMergePatch[V: Encoder](value: V) extends Patch {
    def foldTo[T: Builder]: T = value.encodeTo
    val contentType: String = "application/merge-patch+json"
  }

  final case class StrategicMergePatch[V: Encoder](value: V) extends Patch {
    def foldTo[T: Builder]: T = value.encodeTo
    val contentType: String = "application/strategic-merge-patch+json"
  }

  final case class ServerSideApply[V: Encoder](value: V) extends Patch {
    def foldTo[T: Builder]: T = value.encodeTo
    val contentType: String = "application/apply-patch+yaml"
  }
}
