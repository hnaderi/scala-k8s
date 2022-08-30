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

package dev.hnaderi.sbtk8s

import java.io.File

final case class K8SDeployment(
    name: String,
    namespace: String,
    image: String,
    configs: Map[String, Data] = Map.empty,
    secrets: Map[String, Data] = Map.empty,
    variables: Map[String, String] = Map.empty,
    port: Option[Int] = None,
    host: Option[String] = None,
    path: Option[String] = None
)

sealed trait Data extends Any {
  def getContent: String = this match {
    case Data.InlineString(str) => str
    case Data.FileContent(file) => scala.io.Source.fromFile(file).mkString
  }
  def getBase64Content: String = Utils.base64(getContent)
}

object Data {
  final case class InlineString(value: String) extends AnyVal with Data
  final case class FileContent(file: File) extends AnyVal with Data

  implicit def apply(value: String): Data = InlineString(value)
  implicit def apply(value: File): Data = FileContent(value)
}
