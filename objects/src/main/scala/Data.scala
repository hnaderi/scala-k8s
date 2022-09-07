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

/** A type that represents data content */
trait Data extends Any {
  def getContent: String
  def getBase64Content: String
}

/** A type that represents data content */
object Data extends DataPlatform {
  final case class StringValue(value: String) extends AnyVal with Data {
    def getContent: String = value
    def getBase64Content: String = Utils.base64(value)
  }
  implicit def apply(value: String): StringValue = StringValue(value)
}
