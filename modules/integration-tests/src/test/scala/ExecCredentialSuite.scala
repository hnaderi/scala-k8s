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

class ExecCredentialSuite extends K3sSuite {

  k3sExecPluginClient.test(
    "authenticates against the API server via an exec credential plugin"
  ) { client =>
    APIs.namespaces.list.send(client).map { result =>
      val names = result.items.flatMap(_.metadata.flatMap(_.name)).toSet
      assert(names.contains("kube-system"), s"Expected kube-system in $names")
      assert(names.contains("default"), s"Expected default in $names")
    }
  }
}
