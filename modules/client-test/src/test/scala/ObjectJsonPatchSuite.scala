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

import dev.hnaderi.k8s.Labels
import dev.hnaderi.k8s.client.implicits._
import dev.hnaderi.k8s.utils._
import io.k8s.api.core.v1.Pod
import munit.FunSuite

class ObjectJsonPatchSuite extends FunSuite {
  test("Kubernetes objects json patch builder") {
    assertEquals(
      JsonPatch[Pod].builder
        .add(_.metadata.labels.at(Labels.version), "v1.0.0")
        .build,
      JsonPatch(
        List(
          JsonPatchOp.Add(
            "/metadata/labels/app.kubernetes.io~1version",
            "v1.0.0".encodeTo[KSON]
          )
        )
      )
    )
  }
}
