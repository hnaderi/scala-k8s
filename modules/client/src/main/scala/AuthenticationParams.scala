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

package dev.hnaderi.k8s
package client

final case class AuthenticationParams(
    params: Seq[(String, String)] = Nil,
    headers: Seq[(String, String)] = Nil,
    cookies: Seq[(String, String)] = Nil
)

object AuthenticationParams {
  def params(vs: (String, String)*): AuthenticationParams =
    AuthenticationParams(params = vs)
  def headers(vs: (String, String)*): AuthenticationParams =
    AuthenticationParams(headers = vs)
  def cookies(vs: (String, String)*): AuthenticationParams =
    AuthenticationParams(cookies = vs)

  def basic(username: String, password: String): AuthenticationParams = headers(
    "Authorization" -> Utils.base64(s"$username:$password")
  )
  def bearer(value: String): AuthenticationParams = headers(
    "Authorization" -> s"Bearer $value"
  )

  def from(auth: AuthInfo): AuthenticationParams =
    auth.token
      .map(bearer(_))
      .orElse(
        auth.username
          .zip(auth.password)
          .map { case (user, pass) => basic(user, pass) }
          .headOption
      )
      .getOrElse(AuthenticationParams())

  val empty: AuthenticationParams = AuthenticationParams()
}
