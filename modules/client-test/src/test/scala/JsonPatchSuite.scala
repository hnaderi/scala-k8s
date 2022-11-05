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

import dev.hnaderi.k8s.utils._
import munit.FunSuite

class JsonPatchSuite extends FunSuite {
  test("Sanity") {
    val patch = JsonPatchRaw()
    assertEquals(patch.operations, Nil)
  }

  test("Add") {
    val patch = JsonPatchRaw().add("/a/b", "havij")
    assertEquals(
      patch.operations,
      List(JsonPatchOp.Add("/a/b", "havij".encodeTo[KSON]))
    )
  }

  test("Typed builder") {
    assertEquals(
      JsonPatch[SampleData].builder.add(_.a, 1).operations,
      List(JsonPatchOp.Add("/a", 1.encodeTo[KSON]))
    )
    assertEquals(
      JsonPatch[SampleData].builder.add(_.d.at("key").b, "hello").operations,
      List(JsonPatchOp.Add("/d/key/b", "hello".encodeTo[KSON]))
    )
    assertEquals(
      JsonPatch[SampleData].builder.add(_.c.at(3).e, Nil).operations,
      List(JsonPatchOp.Add("/c/3/e", Seq.empty[Int].encodeTo[KSON]))
    )
  }
}
