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

import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.ZIOKubernetesClient
import zio.Scope
import zio.ZIO
import zio.ZIOAppArgs
import zio.ZIOAppDefault
import zio._

//NOTE run `kubectl proxy` before running this example
object ZIOExample extends ZIOAppDefault {
  private val env = ZIOKubernetesClient.make("http://localhost:8001")

  private val app =
    for {
      n <- ZIOKubernetesClient.send(APIs.nodes.list())
      _ <- ZIO
        .foreach(n.items.map(_.metadata.flatMap(_.name)))(Console.printLine(_))
    } yield ()

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    app.provide(env)

}
