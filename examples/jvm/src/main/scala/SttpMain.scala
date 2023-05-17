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

package example

import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client.APIs
import sttp.client3.circe._
import dev.hnaderi.k8s.client.SttpJdkURLClientBuilder

object SttpMain extends App {
  val client = SttpJdkURLClientBuilder.defaultConfig

  val response = APIs.namespace("default").configmaps.list.send(client)
  response.body.items.flatMap(_.metadata).flatMap(_.name).foreach(println)
}
