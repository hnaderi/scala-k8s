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

package dev.hnaderi.k8s.client

import dev.hnaderi.k8s.utils.Builder
import dev.hnaderi.k8s.utils.Encoder
import dev.hnaderi.k8s.utils.EncoderOps
import dev.hnaderi.k8s.utils.KSON

final case class JsonPatch(operations: List[JsonPatchOp] = Nil) {
  def foldTo[T: Builder]: T = Builder[T].arr(operations.map(_.encodeTo))

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

object JsonPatch {
  implicit val encoder: Encoder[JsonPatch] = new Encoder[JsonPatch] {
    def apply[T: Builder](r: JsonPatch): T = r.foldTo[T]
  }
  def apply[T] = new PartialBuilder[T]

  final class PartialBuilder[T](private val dummy: Boolean = false)
      extends AnyVal {
    def builder[P <: Pointer[T]](implicit p: Pointable[T, P]) =
      new TypedBuilder[T, P](JsonPatch(), p.point(PointerPath()))
  }

  final class TypedBuilder[T, P <: Pointer[T]](val build: JsonPatch, base: P) {
    private def append(op: JsonPatchOp) =
      new TypedBuilder[T, P](build.append(op), base)

    def add[V: Encoder](path: P => Pointer[V], value: V) = append(
      JsonPatchOp.Add(
        path(base).currentPath.toJsonPointer,
        value.encodeTo[KSON]
      )
    )
    def remove[V](path: P => Pointer[V]) = append(
      JsonPatchOp.Remove(path(base).currentPath.toJsonPointer)
    )
    def replace[V: Encoder](path: P => Pointer[V], value: V) = append(
      JsonPatchOp.Replace(
        path(base).currentPath.toJsonPointer,
        value.encodeTo[KSON]
      )
    )
    def test[V: Encoder](path: P => Pointer[V], value: V) = append(
      JsonPatchOp.Test(
        path(base).currentPath.toJsonPointer,
        value.encodeTo[KSON]
      )
    )
    def move[V](from: P => Pointer[V], path: P => Pointer[V]) = append(
      JsonPatchOp.Move(
        from(base).currentPath.toJsonPointer,
        path(base).currentPath.toJsonPointer
      )
    )
    def copy[V](from: P => Pointer[V], path: P => Pointer[V]) = append(
      JsonPatchOp.Copy(
        from(base).currentPath.toJsonPointer,
        path(base).currentPath.toJsonPointer
      )
    )
  }
}
