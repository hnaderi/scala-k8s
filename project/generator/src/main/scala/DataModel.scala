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

  /** Definitions that are declared as an object with no properties, but which
    * actually carry free form contents that the specification cannot express.
    *
    * Unlike a true empty object their wire format is not derivable, so they are
    * treated as primitives and implemented by hand. They are still part of the
    * model, so that anything referring to them keeps resolving.
    */
  private val opaqueObjects: Set[String] = Set(
    "io.k8s.apimachinery.pkg.apis.meta.v1.FieldsV1",
    "io.k8s.apimachinery.pkg.runtime.RawExtension"
  )

  def apply(name: String, definition: Definition): DataModel = {
    val splitIdx = name.lastIndexOf(".")
    val pkgName = name.take(splitIdx)
    val fileName = name.drop(splitIdx + 1)
    apply(pkg = pkgName, name = fileName, defs = definition)
  }

  def apply(name: String, pkg: String, defs: Definition): DataModel = {
    defs.`type` match {
      case Some("object") =>
        val props = ModelProperty(defs)
        val hasKindOrAPIVersion = props.exists(_.isKindOrAPIVersion)

        defs.`x-kubernetes-group-version-kind` match {
          case Some(kinds) if hasKindOrAPIVersion && props.nonEmpty =>
            val cleanedProps = props.filterNot(_.isKindOrAPIVersion)
            kinds match {
              case kind :: Nil =>
                new Resource(
                  name = name,
                  pkg = pkg,
                  description = defs.description,
                  cleanedProps,
                  kind
                )
              case allKinds =>
                new MetaResource(
                  name = name,
                  pkg = pkg,
                  description = defs.description,
                  cleanedProps,
                  allKinds
                )
            }
          case _ if props.nonEmpty =>
            new SubResource(
              name = name,
              pkg = pkg,
              description = defs.description,
              props
            )
          case _ if opaqueObjects.contains(s"$pkg.$name") =>
            new Primitive(
              name = name,
              pkg = pkg,
              description = defs.description
            )
          case _ =>
            new EmptyObject(
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

  /** An `object` with no properties; it always serializes to `{}`.
    *
    * Kubernetes uses these as the variants of a discriminated union (see
    * `x-kubernetes-unions`), where each variant is an empty marker struct.
    */
  final case class EmptyObject(
      name: String,
      pkg: String,
      description: Option[String]
  ) extends DataModel {
    override val properties: Seq[ModelProperty] = Nil
  }

  /** A definition whose wire format is not derivable from the specification,
    * such as a non-object type or an object with free form contents. These are
    * implemented by hand.
    */
  final case class Primitive(
      name: String,
      pkg: String,
      description: Option[String]
  ) extends DataModel {
    override val properties: Seq[ModelProperty] = Nil
  }
}
