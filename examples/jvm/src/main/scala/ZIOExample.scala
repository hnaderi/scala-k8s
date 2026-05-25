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

import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.zio.ZIOKubernetesClient
import zio.*

//NOTE run `kubectl proxy` before running this example
object ZIOExample extends ZIOAppDefault {

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    ZIO.scoped[Any] {
      for {
        client <- ZIOKubernetesClient.defaultConfig
        pods <- APIs.namespace("default").pods.list().send(client)
        names = pods.items.flatMap(_.metadata.flatMap(_.name))
        _ <- ZIO.foreach(names)(Console.printLine(_))
      } yield ()
    }

}
