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

/** Sub-resource info derived from OpenAPI paths */
final case class SubResourceInfo(
    name: String,
    actions: Set[String] // "get", "put", "patch", "post", "connect"
) {
  def isConnect: Boolean = actions.contains("connect")
  def hasGet: Boolean = actions.contains("get")
  def hasReplace: Boolean = actions.contains("put")
  def hasPatch: Boolean = actions.contains("patch")
  def hasCreate: Boolean = actions.contains("post")
}

/** A Kubernetes API resource derived from OpenAPI paths */
final case class ResourceInfo(
    group: String, // "" for core, "apps", "batch", etc.
    version: String, // "v1", "v2", etc.
    kind: String, // "ConfigMap", "Deployment", etc.
    listKind: String, // "ConfigMapList", "DeploymentList", etc.
    fqKind: String, // "io.k8s.api.core.v1.ConfigMap"
    fqListKind: String, // "io.k8s.api.core.v1.ConfigMapList"
    resourceName: String, // "configmaps", "deployments" (k8s plural)
    isNamespaced: Boolean,
    subResources: Seq[SubResourceInfo]
) {
  // Scala sub-package suffix for generated files (e.g. "corev1", "appsv1")
  val packageSuffix: String = {
    val g = if (group.isEmpty) "core" else group.split("\\.").head.toLowerCase
    g + version.filter(c => c.isLetterOrDigit)
  }

  // API group object name (e.g. "CoreV1", "AppsV1", "RbacV1")
  val apiGroupObjectName: String = {
    val base =
      if (group.isEmpty) "Core"
      else {
        val main = group.split("\\.").head
        main.head.toUpper.toString + main.tail
      }
    val ver = "V" + version.stripPrefix("v")
    base + ver
  }

  // Base URL for the API group
  val baseUrl: String =
    if (group.isEmpty) s"/api/$version"
    else s"/apis/$group/$version"
}

/** A Kubernetes API group derived from OpenAPI paths */
final case class APIGroupInfo(
    group: String,
    version: String,
    resources: Seq[ResourceInfo]
)
