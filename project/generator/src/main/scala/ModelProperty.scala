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

import scala.util.matching.Regex

final case class ModelProperty(
    name: String,
    typeName: ModelPropertyType,
    required: Boolean,
    default: Option[String] = None,
    description: Option[String] = None
) {
  def isKindOrAPIVersion: Boolean =
    name == "kind" || name == "apiVersion"
  def fullTypename: String =
    if (required) typeName.name else s"Option[${typeName.name}]"
  def asParam: String = s"$fieldName : $fullTypename$defaultValue"
  private def defaultValue = default.map(v => s" = $v").getOrElse("")

  val dashToCamelName: String = ModelProperty.dashToCamel(name)

  val fieldName: String = name match {
    case "type"                       => "`type`"
    case "object"                     => "`object`"
    case "enum"                       => "`enum`"
    case other if other.contains('-') => s"`$other`"
    case _                            => name
  }
}

object ModelProperty {
  private val pattern: Regex = """([^-]+)-([^-])""".r("head", "tail")
  private def dashToCamel(str: String) = pattern.replaceAllIn(
    str,
    m => {
      val tail = m.group("tail").toUpperCase()
      s"${m.group("head")}$tail"
    }
  )

  def apply(defs: Definition): (Seq[ModelProperty], Boolean) = {
    val required = defs.required.getOrElse(Nil).toSet
    val properties = defs.properties.getOrElse(Map.empty)

    def modelPropertyFor(name: String, p: Property): ModelProperty = {
      val isRequired = required.contains(name)
      ModelProperty(
        name = name,
        typeName = ModelPropertyType(p),
        required = isRequired,
        default = if (isRequired) None else Some("None"),
        description = p.description
      )
    }

    val props = properties
      .map { case (n, p) => modelPropertyFor(n, p) }
      .toSeq
      .sortBy(!_.required)
    val hasKindOrAPIVersion = props.exists(_.isKindOrAPIVersion)
    (props.filterNot(_.isKindOrAPIVersion), hasKindOrAPIVersion)
  }
}
