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

sealed trait DataModel {
  def print: String
  def write(scg: SourceCodeGenerator): Unit
}

object DataModel {
  private final class HeaderWriter(pkg: String, desc: Option[String]) {
    private val sanitizedPkg = pkg.replace('-', '_')
    def apply(imports: String*): String = s"""
package $sanitizedPkg

${imports.map(s => s"import $s\n").mkString}
${Utils.generateDescription(desc)}"""
  }

  def printProps: Seq[ModelProperty] => String =
    _.map(_.asParam).mkString(",\n  ")

  private def codecsFor(name: String) = s"""
//  import io.circe._
//  import io.circe.generic.semiauto._

//  implicit val encoder: Encoder[$name] = deriveEncoder
//  implicit val decoder: Decoder[$name] = deriveDecoder
"""

  private def builderMethod(className: String, prop: ModelProperty): String = {
    val capName =
      prop.dashToCamelName.take(1).toUpperCase() + prop.dashToCamelName.drop(1)
    val value = if (prop.required) "value" else "Some(value)"
    import prop.fieldName
    import prop.typeName

    def result = {
      val construct = if (typeName.isArray) "" else ".toMap"
      if (prop.required) s"$fieldName ++ newValues"
      else s"Some($fieldName.fold(newValues$construct)(_ ++ newValues))"
    }

    val helpers = typeName match {
      case ModelPropertyType.Object(valueType) =>
        s"""
  def add$capName(newValues: (String, $valueType)*) : $className = copy($fieldName = $result)
"""
      case ModelPropertyType.List(valueType) =>
        s"""
  def add$capName(newValues: $valueType*) : $className = copy($fieldName = $result)
"""
      case _ => ""
    }

    s"""  def with$capName(value: ${typeName.name}) : $className = copy($fieldName = $value)$helpers"""
  }
  private def builderMethods(
      className: String,
      props: Seq[ModelProperty]
  ): String = props
    .filterNot(_.isKindOrAPIVersion)
    .map(builderMethod(className, _))
    .mkString("\n")

  def apply(name: String, definition: Definition): DataModel = {
    val splitIdx = name.lastIndexOf(".")
    val pkgName = name.take(splitIdx)
    val fileName = name.drop(splitIdx + 1)
    apply(pkg = pkgName, name = fileName, defs = definition)
  }

  def apply(name: String, pkg: String, defs: Definition): DataModel = {
    val hw = new HeaderWriter(pkg, defs.description)
    defs.`type` match {
      case Some("object") =>
        val (props, hasKindOrAPIVersion) = ModelProperty(defs)

        defs.`x-kubernetes-group-version-kind` match {
          case Some(kind :: Nil) if hasKindOrAPIVersion =>
            new Resource(name = name, pkg = pkg, hw, props, kind)
          case Some(kinds) if hasKindOrAPIVersion =>
            new CommonResource(name = name, pkg = pkg, hw, props, kinds)
          case other =>
            new Object(name = name, pkg = pkg, hw, props)
        }
      case other =>
        new Other(name = name, pkg = pkg, hw)
    }
  }

  final class Object(
      name: String,
      pkg: String,
      header: HeaderWriter,
      properties: Seq[ModelProperty]
  ) extends DataModel {
    def print: String = s"""
${header()}
final case class $name(
  ${printProps(properties)}
) {
${builderMethods(name, properties)}
}
"""

    def write(scg: SourceCodeGenerator): Unit =
      scg.managed(pkg, name).write(print)
  }

  final class Resource(
      name: String,
      pkg: String,
      header: HeaderWriter,
      properties: Seq[ModelProperty],
      kind: Kind
  ) extends DataModel {

    def print: String = s"""
${header("dev.hnaderi.k8s._")}
final case class $name(
  ${printProps(properties.filterNot(_.isKindOrAPIVersion))}
) extends ResourceKind {
   val group = "${kind.group}"
   val kind = "${kind.kind}"
   val version = "${kind.version}"

${builderMethods(name, properties)}
}
"""
    def write(scg: SourceCodeGenerator): Unit =
      scg.managed(pkg, name).write(print)
  }

  final class CommonResource(
      name: String,
      pkg: String,
      header: HeaderWriter,
      properties: Seq[ModelProperty],
      kinds: Seq[Kind]
  ) extends DataModel {
    val kind = kinds.head
    private val supportedKinds = kinds
      .map(k =>
        s"""    ResourceKind("${k.group}", "${k.kind}", "${k.version}")"""
      )
      .mkString(",\n")

    def print: String = s"""
${header("dev.hnaderi.k8s._")}

sealed abstract case class $name(
  ${printProps(properties)}
) extends ResourceKind

object $name {
  def apply(
    _group: String,
    _kind: String,
    _version: String,
    ${printProps(properties)}
  ) : $name = new $name(
   ${properties.map(_.name).map(n => s"      $n = $n").mkString(",\n")}
) {
   val group = _group
   val kind = _kind
   val version = _version
}
  val knownKinds = Seq(
$supportedKinds
  )
}
"""
    def write(scg: SourceCodeGenerator): Unit =
      scg.managed(pkg, name).write(print)
  }

  final class Other(
      name: String,
      pkg: String,
      header: HeaderWriter
  ) extends DataModel {
    def print: String = s"""
${header()}
trait $name
object $name {

}
"""
    def write(scg: SourceCodeGenerator): Unit =
      scg.unmanaged(pkg, name).write(print)
  }
}
