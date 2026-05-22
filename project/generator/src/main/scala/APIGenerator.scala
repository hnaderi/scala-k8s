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

object APIGenerator {

  def write(scg: SourceCodeGenerator, models: APIModels): Unit = {
    models.resources.foreach(writeResource(scg))
    models.groupTraits.foreach(writeGroupTrait(scg))
    models.namespacedTraits.foreach(writeNamespacedTrait(scg))
  }

  private def writeResource(scg: SourceCodeGenerator)(
      m: APIResourceModel
  ): Unit =
    scg.managed(m.pkg, m.name).write(resourceCode(m))

  private def resourceCode(m: APIResourceModel): String = m match {
    case n: NamespacedAPIResourceModel => namespacedCode(n)
    case c: ClusterAPIResourceModel    => clusterCode(c)
  }

  private def namespacedCode(m: NamespacedAPIResourceModel): String = {
    val baseClass =
      if (m.scalable) s"${m.apiGroupObject}.ScalableNamespacedResourceAPI"
      else s"${m.apiGroupObject}.NamespacedResourceAPI"
    val buildersClass =
      if (m.scalable) s"${m.name}.ScalableNamespacedAPIBuilders"
      else s"${m.name}.NamespacedAPIBuilders"
    s"""package dev.hnaderi.k8s.client
       |package ${m.pkg}
       |
       |import ${m.fqKind}
       |import ${m.fqListKind}
       |
       |object ${m.name}
       |    extends $baseClass[${m.kind}, ${m.listKind}](
       |      "${m.resourceName}"
       |    )
       |
       |final case class ${m.name}(namespace: String)
       |    extends $buildersClass
       |
       |object ${m.clusterName} extends ${m.name}.ClusterwideAPIBuilders
       |""".stripMargin
  }

  private def clusterCode(m: ClusterAPIResourceModel): String = {
    val baseClass =
      if (m.scalable) s"${m.apiGroupObject}.ScalableClusterResourceAPI"
      else s"${m.apiGroupObject}.ClusterResourceAPI"
    s"""package dev.hnaderi.k8s.client
       |package ${m.pkg}
       |
       |import ${m.fqKind}
       |import ${m.fqListKind}
       |
       |object ${m.name}
       |    extends $baseClass[${m.kind}, ${m.listKind}](
       |      "${m.resourceName}"
       |    )
       |""".stripMargin
  }

  private def writeGroupTrait(scg: SourceCodeGenerator)(
      m: APIGroupTraitModel
  ): Unit =
    scg.managed("apis", m.name).write(groupTraitCode(m))

  private def groupTraitCode(m: APIGroupTraitModel): String = {
    val fields =
      m.fields.map { case (n, a) => s"  final val $n = $a" }.mkString("\n")
    s"""package dev.hnaderi.k8s.client
       |
       |import ${m.importPkg}._
       |
       |trait ${m.name} {
       |$fields
       |}
       |
       |object ${m.name} extends APIGroupAPI("${m.baseUrl}") with ${m.name}
       |""".stripMargin
  }

  private def writeNamespacedTrait(scg: SourceCodeGenerator)(
      m: APINamespacedTraitModel
  ): Unit =
    scg.managed("apis", m.name).write(namespacedTraitCode(m))

  private def namespacedTraitCode(m: APINamespacedTraitModel): String = {
    val fields = m.fields
      .map { case (n, a) => s"  final val $n: $a = $a(namespace)" }
      .mkString("\n")
    s"""package dev.hnaderi.k8s.client
       |
       |import ${m.importPkg}._
       |
       |trait ${m.name} { self: NamespacedAPI =>
       |$fields
       |}
       |""".stripMargin
  }
}
