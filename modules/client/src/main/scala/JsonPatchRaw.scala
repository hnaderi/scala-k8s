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

final case class JsonPatchRaw(operations: List[JsonPatchOp] = Nil) {
  def foldTo[T: Builder]: T = Builder[T].arr(operations.map(_.encodeTo))

  def append(op: JsonPatchOp*): JsonPatchRaw = JsonPatchRaw(operations ++ op)

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
object JsonPatchRaw {
  implicit val encoder: Encoder[JsonPatchRaw] = new Encoder[JsonPatchRaw] {
    def apply[T: Builder](r: JsonPatchRaw): T = r.foldTo[T]
  }
}
