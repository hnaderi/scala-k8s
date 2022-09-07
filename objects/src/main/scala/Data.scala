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

import java.io.File
import java.net.URI
import java.nio.file.Path

/** A type that represents data content */
trait Data extends Any {
  def getContent: String
  def getBase64Content: String
}

/** A type that represents data content */
object Data {
  final case class StringValue(value: String) extends AnyVal with Data {
    def getContent: String = value
    def getBase64Content: String = Utils.base64(value)
  }
  final case class FileValue(value: File) extends AnyVal with Data {
    def getContent: String = scala.io.Source.fromFile(value).mkString
    def getBase64Content: String = Utils.base64(getContent)
  }

  implicit def apply(value: String): StringValue = StringValue(value)
  implicit def apply(value: File): FileValue = FileValue(value)
  implicit def apply(value: Path): FileValue = file(value)
  implicit def apply(value: URI): FileValue = file(value)
  def file(path: String): FileValue = FileValue(new File(path))
  def file(uri: URI): FileValue = FileValue(new File(uri))
  def file(path: Path): FileValue = FileValue(path.toFile())

  /** creates a data map for all files in a given directory
    * @note
    *   this is not a safe operation and has side effects
    * @throws Exception
    */
  def fromDir(path: File): Map[String, FileValue] = {
    val files = path.listFiles()
    if (files == null) Map.empty
    else files.toList.map(f => (f.getName(), Data(f))).toMap
  }

  /** creates a data map for all files in a given directory
    * @note
    *   this is not a safe operation and has side effects
    * @throws Exception
    */
  def fromDir(path: Path): Map[String, FileValue] = fromDir(path.toFile())
}

/** Utility for filling data in `ConfigMap` or `Secret` */
object DataMap {
  private implicit class MapOps(m: Map[String, Data]) {
    def vMap[T](f: Data => T): Map[String, T] =
      m.map { case (k, v) => (k, f(v)) }.toMap
  }

  /** String data map, useful for `ConfigMap` data and `Secret` stringData
    * @note
    *   this is not a safe operation and might have side effects
    * @throws Exception
    */
  def apply(values: (String, Data)*): Map[String, String] =
    values.toMap.vMap(_.getContent)

  /** Binary base64 encoded data map, useful for `ConfigMap` binaryData and
    * `Secret` data
    * @note
    *   this is not a safe operation and might have side effects
    * @throws Exception
    */
  def binary(values: (String, Data)*): Map[String, String] =
    values.toMap.vMap(_.getBase64Content)

  /** String data map from all files in a directory, keys are file names and
    * values are file content
    * @note
    *   this is not a safe operation and might have side effects
    * @throws Exception
    */
  def fromDir(path: File): Map[String, String] = apply(
    Data.fromDir(path).toSeq: _*
  )

  /** Binary data map from all files in a directory, keys are file names and
    * values are file content
    * @note
    *   this is not a safe operation and might have side effects
    * @throws Exception
    */
  def binaryFromDir(path: File): Map[String, String] = binary(
    Data.fromDir(path).toSeq: _*
  )
}
