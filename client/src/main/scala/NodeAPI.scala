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

import io.k8s.api.core.v1.NodeList
import dev.hnaderi.k8s.utils._

final case class NodeAPI(name: String) {}

object NodeAPI {
  final case class List()
      extends HttpRequest[NodeList]
      with WatchRequest[NodeList] {
    override def run[F[_]](
        http: HttpClient[F]
    ): F[NodeList] = ???
    override def start[F[_]](
        http: StreamingClient[F]
    ): F[NodeList] = ???
  }
  val list = List()
}
