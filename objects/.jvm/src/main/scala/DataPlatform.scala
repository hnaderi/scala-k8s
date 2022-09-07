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

import java.nio.file.Path
import java.nio.file.Paths
import java.io.File
import java.net.URI

final case class FileValue(value: Path) extends AnyVal with Data {
  def getContent: String = scala.io.Source.fromFile(value.toFile()).mkString
  def getBase64Content: String = Utils.base64(getContent)
}
trait DataPlatform {

  implicit def apply(value: File): FileValue = FileValue(value.toPath())
  implicit def apply(value: Path): FileValue = file(value)
  implicit def apply(value: URI): FileValue = file(value)
  def file(path: String): FileValue = FileValue(Paths.get(path))
  def file(uri: URI): FileValue = FileValue(new File(uri).toPath())
  def file(path: Path): FileValue = FileValue(path)

  /** creates a data map for all files in a given directory
    * @note
    *   this is not a safe operation and has side effects and might throw
    *   exceptions too
    */
  def fromDir(path: File): Map[String, FileValue] = {
    val files = path.listFiles()
    if (files == null) Map.empty
    else files.toList.map(f => (f.getName(), Data(f))).toMap
  }

  /** creates a data map for all files in a given directory
    * @note
    *   this is not a safe operation and has side effects and might throw
    *   exceptions too
    */
  def fromDir(path: Path): Map[String, FileValue] = fromDir(path.toFile())
}

trait DataMapPlatform {
  def apply(values: Map[String, Data]): Map[String, String]
  def binary(values: Map[String, Data]): Map[String, String]

  /** String data map from all files in a directory, keys are file names and
    * values are file content
    * @note
    *   this is not a safe operation and might have side effects and throw
    *   exceptions too
    */
  def fromDir(path: File): Map[String, String] = apply(Data.fromDir(path))

  /** Binary data map from all files in a directory, keys are file names and
    * values are file content
    * @note
    *   this is not a safe operation and might have side effects and throw
    *   exceptions too
    */
  def binaryFromDir(path: File): Map[String, String] = binary(
    Data.fromDir(path)
  )
}
