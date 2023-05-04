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

package test

import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.SttpKubernetesClient
import sttp.client3.circe._
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.CustomResourceDefinitionNames

object SttpMain extends App {
  val client = SttpKubernetesClient.urlClient.defaultConfig
  // val client = SttpKubernetesClient.httpClientSync().defaultConfig

  // CustomResourceDefinitionNames

  val response = APIs.namespace("hnaderi").configmaps.list.send(client)
  response.body.items.flatMap(_.metadata).flatMap(_.name).foreach(println)
}
