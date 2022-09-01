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

final case class ModelProperty(
    name: String,
    typeName: String,
    default: Option[String] = None
) {
  def isKindOrAPIVersion: Boolean = name == "kind" || name == "apiVersion"
}

object ModelProperties {
  def apply(defs: Definition): (Seq[ModelProperty], Boolean) = {
    val required = defs.required.getOrElse(Nil).toSet
    val properties = defs.properties.getOrElse(Map.empty)

    def modelPropertyFor(name: String, p: Property): ModelProperty = {
      val sname = sanitizeName(name)
      if (required.contains(name)) ModelProperty(sname, typeName(p))
      else ModelProperty(sname, s"Option[${typeName(p)}]", Some("None"))
    }

    val props = properties.map { case (n, p) => modelPropertyFor(n, p) }.toSeq
    val hasKindOrAPIVersion = props.exists(_.isKindOrAPIVersion)
    (props.filterNot(_.isKindOrAPIVersion), hasKindOrAPIVersion)
  }

  private def typeName(prop: Property): String = {
    baseTypeName(prop).getOrElse("") match {
      case "object" =>
        val valueType = prop.additionalProperties.map(typeName).get
        s"Map[String, $valueType]"
      case "array" =>
        val itemType = prop.items.map(typeName).get
        s"Seq[$itemType]"
      case other => simpleTypeName(other, prop.format)
    }
  }
  private def baseTypeName(prop: Property) = prop.$ref.orElse(prop.`type`)
  private def simpleTypeName(
      tpe: String,
      format: Option[String] = None
  ): String = tpe.trim match {
    case RefName(name) => name
    case "string"      => "String"
    case "integer"     => "Integer"
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

  private def sanitizeName(name: String) = name.replace('-', '_') match {
    case "type"   => "`type`"
    case "object" => "`object`"
    case other    => other
  }
}
