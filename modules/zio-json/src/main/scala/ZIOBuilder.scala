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

package dev.hnaderi.k8s.zioJson

import dev.hnaderi.k8s.utils._
import zio.Chunk
import zio.json.ast.Json

private[zioJson] object ZIOBuilder extends Builder[Json] {

  override def of(str: String): Json = Json.Str(str)

  override def of(i: Int): Json = Json.Num(i)

  override def of(l: Long): Json = Json.Num(l)

  override def of(l: Double): Json = Json.Num(l)

  override def of(b: Boolean): Json = Json.Bool(b)

  override def arr(a: Iterable[Json]): Json = Json.Arr(Chunk.fromIterable(a))

  override def obj(values: Iterable[(String, Json)]): Json =
    Json.Obj(Chunk.fromIterable(values.toMap))

  override def nil: Json = Json.Null

}
