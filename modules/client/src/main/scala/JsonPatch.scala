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

object JsonPatch {
  implicit def encoder[O, P <: Pointer[O]]: Encoder[JsonPatch[O, P]] =
    new Encoder[JsonPatch[O, P]] {
      def apply[T: Builder](r: JsonPatch[O, P]): T =
        Builder[T].arr(r.operations.map(_.encodeTo))
    }
  def apply[T] = new PartialBuilder[T]

  final class PartialBuilder[T](private val dummy: Boolean = false)
      extends AnyVal {
    def builder[P <: Pointer[T]](implicit p: Pointable[T, P]) =
      new JsonPatch[T, P](p.point(PointerPath()))
  }

}
final class JsonPatch[T, +P <: Pointer[T]] private (
    base: P,
    val operations: List[JsonPatchOp] = Nil
) {
  def toRaw: JsonPatchRaw = JsonPatchRaw(operations)
  private def append(op: JsonPatchOp*) =
    new JsonPatch[T, P](base, operations ++ op)

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
  def move[V](from: P => Pointer[V], path: P => Pointer[V]) =
    append(
      JsonPatchOp.Move(
        from(base).currentPath.toJsonPointer,
        path(base).currentPath.toJsonPointer
      )
    )
  def copy[V](from: P => Pointer[V], path: P => Pointer[V]) =
    append(
      JsonPatchOp.Copy(
        from(base).currentPath.toJsonPointer,
        path(base).currentPath.toJsonPointer
      )
    )
}
