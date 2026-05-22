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

sealed trait APIResourceModel {
  def pkg: String
  def name: String
}

final case class NamespacedAPIResourceModel(
    pkg: String,
    name: String,
    clusterName: String,
    fqKind: String,
    fqListKind: String,
    kind: String,
    listKind: String,
    resourceName: String,
    apiGroupObject: String,
    scalable: Boolean
) extends APIResourceModel

final case class ClusterAPIResourceModel(
    pkg: String,
    name: String,
    fqKind: String,
    fqListKind: String,
    kind: String,
    listKind: String,
    resourceName: String,
    apiGroupObject: String,
    scalable: Boolean
) extends APIResourceModel

final case class APIGroupTraitModel(
    name: String,
    baseUrl: String,
    importPkg: String,
    fields: Seq[(String, String)]
)

final case class APINamespacedTraitModel(
    name: String,
    importPkg: String,
    fields: Seq[(String, String)]
)

final case class APIModels(
    resources: Seq[APIResourceModel],
    groupTraits: Seq[APIGroupTraitModel],
    namespacedTraits: Seq[APINamespacedTraitModel]
)

object APIModel {

  def build(
      allResources: Seq[ResourceInfo],
      skipKinds: Set[String],
      traitSkipKinds: Set[String],
      skipGroups: Set[(String, String)]
  ): APIModels = {
    val multiVersionFqKinds = computeMultiVersionKinds(allResources)
    val byGroup = allResources.groupBy(r => (r.group, r.version))

    val resources = byGroup.values.flatMap { groupResources =>
      groupResources
        .filterNot(r => skipKinds.contains(r.fqKind))
        .map(r => buildResourceModel(r, groupResources))
    }.toSeq

    val groupTraits = byGroup.toSeq.collect {
      case (gv, groupResources) if !skipGroups.contains(gv) =>
        val traitResources =
          groupResources.filterNot(r => traitSkipKinds.contains(r.fqKind))
        buildGroupTraitModel(traitResources, multiVersionFqKinds)
    }.flatten

    val namespacedTraits = byGroup.toSeq.collect {
      case (gv, groupResources) if !skipGroups.contains(gv) =>
        val traitResources =
          groupResources.filterNot(r => traitSkipKinds.contains(r.fqKind))
        buildNamespacedTraitModel(traitResources, multiVersionFqKinds)
    }.flatten

    APIModels(resources, groupTraits, namespacedTraits)
  }

  // Kinds that appear in multiple versions of the same group need version-suffixed field names
  // to avoid collisions when multiple version traits are mixed in (e.g. HPA v1 and v2).
  private def computeMultiVersionKinds(
      resources: Seq[ResourceInfo]
  ): Set[String] =
    resources
      .groupBy(_.group)
      .values
      .flatMap { groupResources =>
        val multiVersion = groupResources
          .groupBy(_.kind)
          .filter { case (_, rs) => rs.map(_.version).distinct.size > 1 }
          .keySet
        groupResources.filter(r => multiVersion.contains(r.kind)).map(_.fqKind)
      }
      .toSet

  private def apiClassName(
      r: ResourceInfo,
      groupResources: Seq[ResourceInfo]
  ): String = {
    val multi = groupResources.count(_.kind == r.kind) > 1
    if (multi) r.kind + r.version.head.toUpper + r.version.tail + "API"
    else r.kind + "API"
  }

  private def clusterBuilderName(
      apiName: String,
      groupResources: Seq[ResourceInfo]
  ): String = {
    val kindPart = apiName.stripSuffix("API")
    val hasConflict = groupResources.exists(_.kind == "Cluster" + kindPart)
    if (hasConflict) "Cluster" + kindPart + "ListAPI"
    else "Cluster" + kindPart + "API"
  }

  private def hasScale(r: ResourceInfo): Boolean =
    r.subResources.exists(s => s.name == "scale" && !s.isConnect)

  private def fieldName(
      r: ResourceInfo,
      multiVersionFqKinds: Set[String]
  ): String = {
    val words = splitPascal(r.kind)
    val camel = words match {
      case Nil           => r.kind.toLowerCase
      case first :: rest => first.head.toLower + first.tail + rest.mkString
    }
    val base = if (camel.endsWith("s")) camel else camel + "s"
    if (multiVersionFqKinds.contains(r.fqKind))
      base + r.version.head.toUpper + r.version.tail
    else base
  }

  private def splitPascal(s: String): List[String] = {
    val buf = new StringBuilder
    val words = List.newBuilder[String]
    s.foreach { c =>
      if (c.isUpper && buf.nonEmpty) { words += buf.toString(); buf.clear() }
      buf += c
    }
    if (buf.nonEmpty) words += buf.toString()
    words.result()
  }

  private def buildResourceModel(
      r: ResourceInfo,
      groupResources: Seq[ResourceInfo]
  ): APIResourceModel = {
    val apiName = apiClassName(r, groupResources)
    val pkg = s"apis.${r.packageSuffix}"
    if (r.isNamespaced)
      NamespacedAPIResourceModel(
        pkg = pkg,
        name = apiName,
        clusterName = clusterBuilderName(apiName, groupResources),
        fqKind = r.fqKind,
        fqListKind = r.fqListKind,
        kind = r.kind,
        listKind = r.listKind,
        resourceName = r.resourceName,
        apiGroupObject = r.apiGroupObjectName,
        scalable = hasScale(r)
      )
    else
      ClusterAPIResourceModel(
        pkg = pkg,
        name = apiName,
        fqKind = r.fqKind,
        fqListKind = r.fqListKind,
        kind = r.kind,
        listKind = r.listKind,
        resourceName = r.resourceName,
        apiGroupObject = r.apiGroupObjectName,
        scalable = hasScale(r)
      )
  }

  private def buildGroupTraitModel(
      resources: Seq[ResourceInfo],
      multiVersionFqKinds: Set[String]
  ): Option[APIGroupTraitModel] = {
    if (resources.isEmpty) return None
    val sample = resources.head
    val fields = resources.map { r =>
      val apiName = apiClassName(r, resources)
      val accessor =
        if (r.isNamespaced) clusterBuilderName(apiName, resources)
        else apiName
      fieldName(r, multiVersionFqKinds) -> accessor
    }
    Some(
      APIGroupTraitModel(
        name = sample.apiGroupObjectName,
        baseUrl = sample.baseUrl,
        importPkg = s"apis.${sample.packageSuffix}",
        fields = fields
      )
    )
  }

  private def buildNamespacedTraitModel(
      resources: Seq[ResourceInfo],
      multiVersionFqKinds: Set[String]
  ): Option[APINamespacedTraitModel] = {
    val nsResources = resources.filter(_.isNamespaced)
    if (nsResources.isEmpty) return None
    val sample = resources.head
    val fields = nsResources.map { r =>
      val apiName = apiClassName(r, resources)
      fieldName(r, multiVersionFqKinds) -> apiName
    }
    Some(
      APINamespacedTraitModel(
        name = sample.apiGroupObjectName + "Namespaced",
        importPkg = s"apis.${sample.packageSuffix}",
        fields = fields
      )
    )
  }
}
