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

import java.io.File

sealed trait DataModel {
  def print: String
  def write(file: File): Unit = Utils.writeOutput(file, print)
  def clean(file: File): Unit = file.delete()
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
    s"""  def with$capName(value: ${prop.typeName}) : $className = copy(${prop.fieldName} = $value)"""
  }
  private def builderMethods(
      className: String,
      props: Seq[ModelProperty]
  ): String = props
    .filterNot(_.isKindOrAPIVersion)
    .map(builderMethod(className, _))
    .mkString("\n")

  def apply(name: String, pkg: String, defs: Definition): DataModel = {
    val hw = new HeaderWriter(pkg, defs.description)
    defs.`type` match {
      case Some("object") =>
        val (props, hasKindOrAPIVersion) = ModelProperties(defs)

        defs.`x-kubernetes-group-version-kind` match {
          case Some(kind :: Nil) if hasKindOrAPIVersion =>
            println(s"Resource $name $kind")
            new Resource(name, hw, props, kind)
          case Some(kinds) if hasKindOrAPIVersion =>
            new CommonResource(name, hw, props, kinds)
          case other =>
            println(s"Others: $other")
            new Object(name, hw, props)
        }
      case other =>
        println(s"Not object, $other")
        new Other(name, hw)
    }
  }

  final class Object(
      name: String,
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
object $name {
${codecsFor(name)}
}
"""
  }

  final class Resource(
      name: String,
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
object $name {
${codecsFor(name)}
}
"""
  }

  final class CommonResource(
      name: String,
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
${codecsFor(name)}
}
"""
  }

  final class Other(
      name: String,
      header: HeaderWriter
  ) extends DataModel {
    def print: String = s"""
${header()}
trait $name
object $name {

}
"""
    override def write(file: File): Unit = {
      if (!file.exists()) Utils.writeOutput(file, print)
    }
    override def clean(file: File): Unit = ()
  }
}
