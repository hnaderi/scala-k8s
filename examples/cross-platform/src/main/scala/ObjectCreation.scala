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

//> using dep "dev.hnaderi::scala-k8s-manifests:0.20.1"

package example

import dev.hnaderi.k8s.manifest._
import dev.hnaderi.k8s.implicits._
import io.k8s.api.core.v1.ConfigMap
import io.k8s.api.core.v1.Secret
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

object ObjectCreation extends App {
  val configMap = ConfigMap(
    metadata = ObjectMeta(name = "example"),
    data = Map("test" -> "value")
  )

  val secret = Secret(
    metadata = ObjectMeta(name = "example"),
    data = Map("test" -> "value")
  )

  val objs = Seq(configMap, secret)

  val manifest = objs.asManifest

  println(manifest)
}
