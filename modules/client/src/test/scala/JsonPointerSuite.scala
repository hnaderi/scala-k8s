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

import munit.FunSuite
import munit.Location

import Pointer.Plain

class JsonPointerSuite extends FunSuite {
  private def assertPath[T](p: Pointer[T], jp: String)(implicit loc: Location) =
    assertEquals(p.path.toJsonPointer, jp)

  test("Root") {
    assertPath[SampleData](Pointer[SampleData].root, "")
  }

  test("simple path") {
    assertPath[Int](
      Pointer[SampleData].root.a,
      "/a"
    )
    assertPath[String](
      Pointer[SampleData].root.b,
      "/b"
    )
    assertPath[List[SampleData]](
      Pointer[SampleData].root.c,
      "/c"
    )
    assertPath[Map[String, SampleData]](
      Pointer[SampleData].root.d,
      "/d"
    )
  }

  test("list index") {
    assertPath[SampleData](
      Pointer[SampleData].root.c.at(2),
      "/c/2"
    )
    assertPath[SampleData](
      Pointer[SampleData].root.c.last,
      "/c/-"
    )
    assertPath[Int](
      Pointer[SampleData].root.c.at(0).a,
      "/c/0/a"
    )
  }

  test("map key") {
    assertPath[SampleData](
      Pointer[SampleData].root.d.at("key"),
      "/d/key"
    )
    assertPath[String](
      Pointer[SampleData].root.d.at("key").b,
      "/d/key/b"
    )
  }
}
