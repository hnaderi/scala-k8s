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

import Pointer.Plain
import dev.hnaderi.k8s.utils._

final case class SampleData(
    a: Int,
    b: String,
    c: List[SampleData],
    d: Map[String, SampleData],
    e: List[Int]
)

object SampleData {
  final case class SampleDataPointer(currentPath: PointerPath = PointerPath())
      extends Pointer[SampleData] {
    def a: Plain[Int] = Plain(currentPath / "a")
    def b: Plain[String] = Plain(currentPath / "b")
    def c: ListPointer[SampleData] = ListPointer(currentPath / "c")
    def d: MapPointer[SampleData] = MapPointer(currentPath / "d")
    def e: ListPointer[Int] = ListPointer(currentPath / "e")
  }
  implicit val pointableInstance: Pointable[SampleData, SampleDataPointer] =
    Pointable(
      SampleDataPointer(_)
    )

  implicit def encoderInstance: Encoder[SampleData] = new Encoder[SampleData] {
    def apply[T: Builder](r: SampleData): T = {
      val obj = ObjectWriter[T]()

      obj
        .write("a", r.a)
        .write("b", r.b)
        .write("c", r.c)
        .write("d", r.d)
        .write("e", r.e)
        .build
    }
  }
}
