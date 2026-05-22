package dev.hnaderi.k8s.generator

import munit.FunSuite
import java.nio.file.Files
import Fixtures._

class APIGeneratorSuite extends FunSuite {

  private def generate(resources: Seq[ResourceInfo]): Map[String, String] = {
    val managed = Files.createTempDirectory("scg-managed").toFile
    val unmanaged = Files.createTempDirectory("scg-unmanaged").toFile
    managed.deleteOnExit()
    unmanaged.deleteOnExit()
    val scg = new SourceCodeGenerator(managed = managed, unmanaged = unmanaged)
    val models = APIModel.build(resources, Set.empty, Set.empty, Set.empty)
    APIGenerator.write(scg, models)
    scg.createdFiles.map { f =>
      f.getName.stripSuffix(".scala") -> scala.io.Source.fromFile(f).mkString
    }.toMap
  }

  // ---- namespaced resource templates ----

  test("scalable namespaced resource uses ScalableNamespacedResourceAPI") {
    val dep = resource(
      "Deployment",
      "deployments",
      group = "apps",
      subResources = Seq(scaleSubResource)
    )
    val code = generate(Seq(dep))("DeploymentAPI")
    assert(
      code.contains("ScalableNamespacedResourceAPI"),
      s"expected ScalableNamespacedResourceAPI, got:\n$code"
    )
    assert(code.contains("ScalableNamespacedAPIBuilders"))
  }

  test("non-scalable namespaced resource uses NamespacedResourceAPI") {
    val cm = resource("ConfigMap", "configmaps")
    val code = generate(Seq(cm))("ConfigMapAPI")
    assert(
      code.contains("NamespacedResourceAPI"),
      s"expected NamespacedResourceAPI, got:\n$code"
    )
    assert(!code.contains("Scalable"))
  }

  test(
    "namespaced resource code includes object, case class, and cluster object"
  ) {
    val cm = resource("ConfigMap", "configmaps")
    val code = generate(Seq(cm))("ConfigMapAPI")
    assert(code.contains("object ConfigMapAPI"))
    assert(code.contains("final case class ConfigMapAPI(namespace: String)"))
    assert(code.contains("object ClusterConfigMapAPI"))
  }

  // ---- cluster-scoped resource templates ----

  test("scalable cluster resource uses ScalableClusterResourceAPI") {
    val node = resource(
      "Node",
      "nodes",
      isNamespaced = false,
      subResources = Seq(scaleSubResource)
    )
    val code = generate(Seq(node))("NodeAPI")
    assert(
      code.contains("ScalableClusterResourceAPI"),
      s"expected ScalableClusterResourceAPI, got:\n$code"
    )
  }

  test("non-scalable cluster resource uses ClusterResourceAPI") {
    val node = resource("Node", "nodes", isNamespaced = false)
    val code = generate(Seq(node))("NodeAPI")
    assert(
      code.contains("ClusterResourceAPI"),
      s"expected ClusterResourceAPI, got:\n$code"
    )
    assert(!code.contains("Scalable"))
  }

  // ---- trait templates ----

  test("group trait contains final val for each resource") {
    val cm = resource("ConfigMap", "configmaps")
    val node = resource("Node", "nodes", isNamespaced = false)
    val files = generate(Seq(cm, node))
    val trait_ = files("CoreV1")
    assert(trait_.contains("final val configMaps"))
    assert(trait_.contains("final val nodes"))
  }

  test("namespaced trait only contains namespaced resources") {
    val cm = resource("ConfigMap", "configmaps")
    val node = resource("Node", "nodes", isNamespaced = false)
    val files = generate(Seq(cm, node))
    val trait_ = files("CoreV1Namespaced")
    assert(trait_.contains("configMaps"))
    assert(
      !trait_.contains("nodes"),
      s"cluster-scoped resource should not appear in namespaced trait"
    )
  }

  test("namespaced trait has self: NamespacedAPI constraint") {
    val cm = resource("ConfigMap", "configmaps")
    val trait_ = generate(Seq(cm))("CoreV1Namespaced")
    assert(trait_.contains("self: NamespacedAPI"))
  }

  test("generated resource file has no license header") {
    val cm = resource("ConfigMap", "configmaps")
    val code = generate(Seq(cm))("ConfigMapAPI")
    assert(
      !code.startsWith("/*"),
      "generated files must not contain a license header"
    )
  }
}
