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
import dev.hnaderi.k8s.implicits._
import io.k8s.api.core.v1.ConfigMap
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

class ConfigMapsSuite extends K3sSuite {

  private val ns = "default"
  private val cmName = "integration-test-cm"

  k3sClient.test("create, get, and delete a ConfigMap") { client =>
    val cm = ConfigMap(
      metadata = ObjectMeta(name = cmName),
      data = Map("key" -> "value")
    )
    for {
      created <- APIs.namespace(ns).configMaps.create(cm).send(client)
      _ = assertEquals(created.metadata.flatMap(_.name), Some(cmName))
      fetched <- APIs.namespace(ns).configMaps.get(cmName).send(client)
      _ = assertEquals(fetched.data, Some(Map("key" -> "value")))
      _ <- APIs.namespace(ns).configMaps.delete(cmName).send(client)
    } yield ()
  }

  k3sClient.test("list ConfigMaps in default namespace") { client =>
    APIs.namespace(ns).configMaps.list().send(client).map { result =>
      assert(result.items != null)
    }
  }
}
