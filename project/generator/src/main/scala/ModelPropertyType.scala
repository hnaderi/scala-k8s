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

sealed abstract class ModelPropertyType {
  val name: String
  val isObject: Boolean
  val isArray: Boolean
}
object ModelPropertyType {
  final case class Object(valueType: String) extends ModelPropertyType {
    val isObject: Boolean = true
    val isArray: Boolean = false
    val name = s"Map[String, $valueType]"
  }
  final case class List(valueType: String) extends ModelPropertyType {
    val isArray: Boolean = true
    val isObject: Boolean = false
    val name = s"Seq[$valueType]"
  }
  final case class Primitive(name: String) extends ModelPropertyType {
    val isArray: Boolean = false
    val isObject: Boolean = false
  }

  def apply(prop: Property): ModelPropertyType = {
    baseTypeName(prop).getOrElse("") match {
      case "object" =>
        val valueType = prop.additionalProperties.map(apply).get.name
        ModelPropertyType.Object(valueType)
      case "array" =>
        val itemType = prop.items.map(apply).get.name
        ModelPropertyType.List(itemType)
      case other =>
        ModelPropertyType.Primitive(simpleTypeName(other, prop.format))
    }
  }
  private def baseTypeName(prop: Property) = prop.$ref.orElse(prop.`type`)
  private def simpleTypeName(
      tpe: String,
      format: Option[String] = None
  ): String = tpe.trim match {
    case RefName(name) => name
    case "string"      => "String"
    case "integer"     => "Int"
    case "boolean"     => "Boolean"
    case "number" =>
      format match {
        case Some("int64")  => "Long"
        case Some("double") => "Double"
        case other => throw new Exception(s"Unknown number format $other")
      }
  }
  private object RefName {
    private val prefix = "#/definitions/"
    def unapply(tpe: String): Option[String] =
      if (tpe.startsWith(prefix))
        Some(tpe.replace(prefix, "").replace('-', '_'))
      else None
  }
}
