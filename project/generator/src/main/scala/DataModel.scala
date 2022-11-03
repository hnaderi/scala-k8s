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

sealed trait DataModel extends Serializable with Product {
  val name: String
  val pkg: String
  val description: Option[String]
  val properties: Seq[ModelProperty]
  final val fullName: String = s"$pkg.$name".replace('-', '_')
}

object DataModel {
  def apply(name: String, definition: Definition): DataModel = {
    val splitIdx = name.lastIndexOf(".")
    val pkgName = name.take(splitIdx)
    val fileName = name.drop(splitIdx + 1)
    apply(pkg = pkgName, name = fileName, defs = definition)
  }

  def apply(name: String, pkg: String, defs: Definition): DataModel = {
    defs.`type` match {
      case Some("object") =>
        val (props, hasKindOrAPIVersion) = ModelProperty(defs)

        defs.`x-kubernetes-group-version-kind` match {
          case Some(kind :: Nil) if hasKindOrAPIVersion && props.nonEmpty =>
            new Resource(
              name = name,
              pkg = pkg,
              description = defs.description,
              props,
              kind
            )
          case Some(kinds) if hasKindOrAPIVersion && props.nonEmpty =>
            new MetaResource(
              name = name,
              pkg = pkg,
              description = defs.description,
              props,
              kinds
            )
          case _ if props.nonEmpty =>
            new SubResource(
              name = name,
              pkg = pkg,
              description = defs.description,
              props
            )
          case _ =>
            new Primitive(
              name = name,
              pkg = pkg,
              description = defs.description
            )
        }
      case _ =>
        new Primitive(name = name, pkg = pkg, description = defs.description)
    }
  }

  final case class SubResource(
      name: String,
      pkg: String,
      description: Option[String],
      properties: Seq[ModelProperty]
  ) extends DataModel

  final case class Resource(
      name: String,
      pkg: String,
      description: Option[String],
      properties: Seq[ModelProperty],
      kind: Kind
  ) extends DataModel

  final case class MetaResource(
      name: String,
      pkg: String,
      description: Option[String],
      properties: Seq[ModelProperty],
      kinds: Seq[Kind]
  ) extends DataModel

  final case class Primitive(
      name: String,
      pkg: String,
      description: Option[String]
  ) extends DataModel {
    override val properties: Seq[ModelProperty] = Nil
  }
}
