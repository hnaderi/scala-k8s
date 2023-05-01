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

import io.k8s.apimachinery.pkg.apis.meta.v1.Status

final case class ErrorResponse(error: ErrorStatus, details: Status)
    extends Exception(
      s"""Request failed!
status: $error
details: $details
"""
    )
sealed trait ErrorStatus extends Serializable with Product
object ErrorStatus {
  case object NotFound extends ErrorStatus
  case object Conflict extends ErrorStatus
  case object BadRequest extends ErrorStatus
  case object Unauthorized extends ErrorStatus
  case object Forbidden extends ErrorStatus
  case class Other(value: Int) extends ErrorStatus
}
