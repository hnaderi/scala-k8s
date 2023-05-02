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

import sttp.client3._

import java.net.http.{HttpClient => JClient}
import java.time.{Duration => JDuration}
import javax.net.ssl.SSLContext
import scala.concurrent.duration._

class SttpJdkHttpSyncClientBuilder(timeout: FiniteDuration = 3.seconds)
    extends SttpJVM[Identity] {
  override protected def buildWithSSLContext
      : SSLContext => SttpBackend[Identity, Any] = ssl => {
    val client = JClient
      .newBuilder()
      .followRedirects(JClient.Redirect.NEVER)
      .connectTimeout(JDuration.ofMillis(timeout.toMillis))
      .sslContext(ssl)
      .build()

    HttpClientSyncBackend.usingClient(client)
  }
}
