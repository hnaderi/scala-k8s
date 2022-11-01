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

package dev.hnaderi.k8s.utils

import io.k8s.apimachinery.pkg.apis.meta.v1.Patch
import munit.FunSuite

class JsonPatchSuite extends FunSuite {
  test("Sanity") {
    val patch = Patch.JsonPatch()
    assertEquals(patch, Patch.JsonPatch(Nil))
  }

  test("Add") {
    val patch = Patch.JsonPatch().add("/a/b", "havij")
    assertEquals(
      patch,
      Patch.JsonPatch(List(JsonPatchOp.Add("/a/b", "havij".encodeTo[KSON])))
    )
  }
}
