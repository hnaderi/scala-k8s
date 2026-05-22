package dev.hnaderi.k8s.generator

import munit.FunSuite
import Fixtures._

class APIModelSuite extends FunSuite {

  private def build(
      resources: Seq[ResourceInfo],
      skipKinds: Set[String] = Set.empty,
      traitSkipKinds: Set[String] = Set.empty,
      skipGroups: Set[(String, String)] = Set.empty
  ): APIModels =
    APIModel.build(resources, skipKinds, traitSkipKinds, skipGroups)

  private def namespacedFields(models: APIModels): Seq[String] =
    models.namespacedTraits.flatMap(_.fields.map(_._1))

  private def groupFields(models: APIModels): Seq[String] =
    models.groupTraits.flatMap(_.fields.map(_._1))

  // ---- field name derivation ----

  test("Endpoints does not produce endpointss") {
    val m = build(Seq(resource("Endpoints", "endpoints")))
    assertEquals(namespacedFields(m), Seq("endpoints"))
  }

  test("ConfigMap becomes configMaps") {
    val m = build(Seq(resource("ConfigMap", "configmaps")))
    assertEquals(namespacedFields(m), Seq("configMaps"))
  }

  test("Pod becomes pods") {
    val m = build(Seq(resource("Pod", "pods")))
    assertEquals(namespacedFields(m), Seq("pods"))
  }

  test("HorizontalPodAutoscaler becomes horizontalPodAutoscalers") {
    val m = build(
      Seq(
        resource(
          "HorizontalPodAutoscaler",
          "horizontalpodautoscalers",
          group = "autoscaling"
        )
      )
    )
    assertEquals(namespacedFields(m), Seq("horizontalPodAutoscalers"))
  }

  // ---- multi-version field names ----

  test("same kind in multiple versions of a group gets version suffix") {
    val v1 = resource(
      "HorizontalPodAutoscaler",
      "horizontalpodautoscalers",
      group = "autoscaling",
      version = "v1"
    )
    val v2 = resource(
      "HorizontalPodAutoscaler",
      "horizontalpodautoscalers",
      group = "autoscaling",
      version = "v2"
    )
    val fields = namespacedFields(build(Seq(v1, v2))).toSet
    assert(
      fields.contains("horizontalPodAutoscalersV1"),
      s"expected V1 suffix, got: $fields"
    )
    assert(
      fields.contains("horizontalPodAutoscalersV2"),
      s"expected V2 suffix, got: $fields"
    )
  }

  test("kind in only one version of a group has no suffix") {
    val dep = resource("Deployment", "deployments", group = "apps")
    val fields = namespacedFields(build(Seq(dep)))
    assertEquals(fields, Seq("deployments"))
  }

  // ---- skipKinds ----

  test("skipKinds excludes kind from generated resource files") {
    val pod = resource("Pod", "pods")
    val cm = resource("ConfigMap", "configmaps")
    val m = build(Seq(pod, cm), skipKinds = Set(pod.fqKind))
    val names = m.resources.map(_.name)
    assert(
      !names.contains("PodAPI"),
      s"PodAPI should not be generated, got: $names"
    )
    assert(names.exists(_.contains("ConfigMap")))
  }

  test("skipKinds does not remove kind from group trait accessor") {
    val pod = resource("Pod", "pods")
    val cm = resource("ConfigMap", "configmaps")
    val m = build(Seq(pod, cm), skipKinds = Set(pod.fqKind))
    assert(
      groupFields(m).contains("pods"),
      s"pods should still appear in group trait (file is hand-maintained)"
    )
  }

  // ---- traitSkipKinds ----

  test("traitSkipKinds removes kind from group and namespaced trait") {
    val pod = resource("Pod", "pods")
    val cm = resource("ConfigMap", "configmaps")
    val m = build(Seq(pod, cm), traitSkipKinds = Set(pod.fqKind))
    assert(
      !groupFields(m).contains("pods"),
      s"pods should be absent from trait, got: ${groupFields(m)}"
    )
    assert(!namespacedFields(m).contains("pods"))
  }

  test("traitSkipKinds does not affect resource file generation") {
    val pod = resource("Pod", "pods")
    val m = build(Seq(pod), traitSkipKinds = Set(pod.fqKind))
    assert(m.resources.map(_.name).contains("PodAPI"))
  }

  // ---- skipGroups ----

  test("skipGroups suppresses group and namespaced traits for that group") {
    val crd = resource(
      "CustomResourceDefinition",
      "customresourcedefinitions",
      group = "apiextensions.k8s.io",
      isNamespaced = false
    )
    val m = build(Seq(crd), skipGroups = Set(("apiextensions.k8s.io", "v1")))
    assert(m.groupTraits.isEmpty)
    assert(m.namespacedTraits.isEmpty)
  }

  test("skipGroups does not suppress resource file generation") {
    val crd = resource(
      "CustomResourceDefinition",
      "customresourcedefinitions",
      group = "apiextensions.k8s.io",
      isNamespaced = false
    )
    val m = build(Seq(crd), skipGroups = Set(("apiextensions.k8s.io", "v1")))
    assert(m.resources.nonEmpty)
  }

  // ---- scalable detection ----

  test("resource with get+put scale subresource is scalable") {
    val dep = resource(
      "Deployment",
      "deployments",
      group = "apps",
      subResources = Seq(scaleSubResource)
    )
    val scalable = build(Seq(dep)).resources
      .collectFirst { case n: NamespacedAPIResourceModel => n.scalable }
    assertEquals(scalable, Some(true))
  }

  test("resource without scale subresource is not scalable") {
    val cm = resource("ConfigMap", "configmaps")
    val scalable = build(Seq(cm)).resources
      .collectFirst { case n: NamespacedAPIResourceModel => n.scalable }
    assertEquals(scalable, Some(false))
  }

  test("connect-only scale subresource does not mark resource as scalable") {
    val r = resource(
      "Something",
      "somethings",
      subResources = Seq(connectScaleSubResource)
    )
    val scalable = build(Seq(r)).resources
      .collectFirst { case n: NamespacedAPIResourceModel => n.scalable }
    assertEquals(scalable, Some(false))
  }

  // ---- namespaced vs cluster-scoped ----

  test("namespaced resource produces NamespacedAPIResourceModel") {
    val dep =
      resource("Deployment", "deployments", group = "apps", isNamespaced = true)
    assert(
      build(Seq(dep)).resources.head.isInstanceOf[NamespacedAPIResourceModel]
    )
  }

  test("cluster-scoped resource produces ClusterAPIResourceModel") {
    val node = resource("Node", "nodes", isNamespaced = false)
    assert(
      build(Seq(node)).resources.head.isInstanceOf[ClusterAPIResourceModel]
    )
  }

  // ---- RBAC cluster name conflict ----

  test("Role gets ClusterRoleListAPI to avoid conflict with ClusterRole") {
    val role = resource(
      "Role",
      "roles",
      group = "rbac.authorization.k8s.io",
      isNamespaced = true
    )
    val clusterRole = resource(
      "ClusterRole",
      "clusterroles",
      group = "rbac.authorization.k8s.io",
      isNamespaced = false
    )
    val m = build(Seq(role, clusterRole))
    val roleModel = m.resources.collectFirst {
      case n: NamespacedAPIResourceModel if n.name == "RoleAPI" => n
    }
    assert(roleModel.isDefined, "RoleAPI resource model not found")
    assertEquals(roleModel.get.clusterName, "ClusterRoleListAPI")
  }

  test("resource without ClusterX conflict uses ClusterXAPI name") {
    val dep = resource("Deployment", "deployments", group = "apps")
    val m = build(Seq(dep))
    val depModel = m.resources.collectFirst {
      case n: NamespacedAPIResourceModel if n.name == "DeploymentAPI" => n
    }
    assert(depModel.isDefined)
    assertEquals(depModel.get.clusterName, "ClusterDeploymentAPI")
  }
}
