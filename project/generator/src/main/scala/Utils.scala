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

package dev.hnaderi.k8s.generator

import cats.implicits._
import io.circe.Decoder
import io.circe.Error
import io.circe.HCursor
import io.circe.Json
import io.circe.JsonObject
import io.circe.parser.parse

import java.io.File
import java.io.PrintWriter
import scala.io.Source

object Utils {
  private val definitionsDecoder: Decoder[Map[String, Definition]] =
    (c: HCursor) =>
      for {
        defs <- c.get[JsonObject]("definitions")
        l <- defs.toList.traverse { case (k, v) =>
          v.as[Definition].map((k, _))
        }
      } yield l.toMap

  def loadFile(file: File): String = Source.fromFile(file).mkString
  def loadJson(file: File): Either[Error, Json] = parse(loadFile(file))
  def loadDefinitions(file: File): Either[Error, Map[String, Definition]] =
    loadJson(file).flatMap(_.as(definitionsDecoder))
  def writeOutput(file: File, content: String): Unit = {
    val printWriter = new PrintWriter(file)

    try {
      printWriter.println(content)
    } finally {
      printWriter.close()
    }
  }

  def generateDescription(description: Option[String]): String =
    description.fold("") { d =>
      val content = d.replace("*/", "*&#47;").replace("/*", "&#47;*")
      s"/** $content */"
    }
}
