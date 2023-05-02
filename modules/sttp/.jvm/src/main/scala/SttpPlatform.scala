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

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

private[client] trait SttpPlatform {

  /** Uses java.net.http.HttpClient asynchronously using Futures. It requires
    * JDK 11+
    *
    * @param timeout
    *   Request timeout
    * @param ec
    *   ExecutionContext to run on
    */
  final def httpClientAsync(timeout: FiniteDuration = 60.seconds)(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpJdkHttpFutureClientBuilder = new SttpJdkHttpFutureClientBuilder(
    timeout
  )

  /** Client using java.net.http.HttpClient in a blocking fashion. It requires
    * JDK 11+
    *
    * @param timeout
    *   Request timeout
    */
  final def httpClientSync(
      timeout: FiniteDuration = 60.seconds
  ): SttpJdkHttpSyncClientBuilder = new SttpJdkHttpSyncClientBuilder(timeout)

  /** Client using java URL */
  final val urlClient = SttpJdkURLClientBuilder
}
