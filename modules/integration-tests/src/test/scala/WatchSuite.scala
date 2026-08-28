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

package dev.hnaderi.k8s.integration

import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.WatchEvent

import scala.concurrent.duration._

class WatchSuite extends K3sSuite {

  private val ns = "default"

  k3sClient.test("an expired resource version arrives as an error event") {
    client =>
      APIs
        .namespace(ns)
        .configMaps
        .list(resourceVersion = Some("1"))
        .listen(client)
        .head
        .compile
        .lastOrError
        .timeout(1.minute)
        .map {
          case WatchEvent.Error(status) =>
            assertEquals(status.code, Some(410))
            assertEquals(status.reason, Some("Expired"))
          case other => fail(s"expected an error event, but got: $other")
        }
  }
}
