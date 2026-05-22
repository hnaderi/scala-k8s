package dev.hnaderi.k8s.generator

import munit.FunSuite
import ModelFixtures._

class ObjectGeneratorSuite extends FunSuite {

  private def code(m: DataModel): String = ObjectGenerator.print(m)

  // ---- Resource ----

  test("Resource generates final case class extending KObject") {
    val c = code(configMap)
    assert(c.contains(s"final case class ConfigMap("), c)
    assert(c.contains("extends KObject"), c)
  }

  test("Resource contains _resourceKind with correct group/kind/version") {
    val c = code(configMap)
    assert(
      c.contains("""_resourceKind = ResourceKind("", "ConfigMap", "v1")"""),
      c
    )
  }

  test("Resource companion contains encoder and decoder") {
    val c = code(configMap)
    assert(c.contains("object ConfigMap"), c)
    assert(c.contains("encoder"), c)
    assert(c.contains("decoder"), c)
  }

  test("Resource package hyphen is replaced with underscore") {
    val hyphenPkg = configMap.copy(pkg = "io.k8s.kube-aggregator.v1")
    assert(
      code(hyphenPkg).startsWith("package io.k8s.kube_aggregator.v1"),
      code(hyphenPkg)
    )
  }

  // ---- SubResource ----

  test("SubResource generates final case class without KObject") {
    val c = code(objectMeta)
    assert(c.contains("final case class ObjectMeta("), c)
    assert(!c.contains("KObject"), c)
    assert(!c.contains("_resourceKind"), c)
  }

  test("SubResource companion contains encoder and decoder") {
    val c = code(objectMeta)
    assert(c.contains("object ObjectMeta"), c)
    assert(c.contains("encoder"), c)
    assert(c.contains("decoder"), c)
  }

  // ---- MetaResource ----

  test("MetaResource generates case class with kind and apiVersion params") {
    val c = code(watchEvent)
    assert(c.contains("final case class WatchEvent("), c)
    assert(c.contains("kind: String"), c)
    assert(c.contains("apiVersion: String"), c)
  }

  test("MetaResource generates knownKinds sequence with all kinds") {
    val c = code(watchEvent)
    assert(c.contains("val knownKinds"), c)
    assert(c.contains("""ResourceKind("", "ConfigMap", "v1")"""), c)
    assert(c.contains("""ResourceKind("apps", "Deployment", "v1")"""), c)
  }

  // ---- Primitive ----

  test("Primitive generates a trait") {
    val c = code(intOrString)
    assert(c.contains("trait IntOrString"), c)
    assert(!c.contains("final case class"), c)
  }

  test("Primitive trait has encoder and decoder stubs") {
    val c = code(intOrString)
    assert(c.contains("Encoder"), c)
    assert(c.contains("Decoder"), c)
  }

  // ---- builder methods ----

  test("every property gets a with* builder") {
    val c = code(configMap)
    assert(c.contains("def withName"), c)
    assert(c.contains("def withNamespace"), c)
    assert(c.contains("def withLabels"), c)
  }

  test("every property gets a map* builder") {
    val c = code(configMap)
    assert(c.contains("def mapName"), c)
    assert(c.contains("def mapNamespace"), c)
    assert(c.contains("def mapLabels"), c)
  }

  test("map property gets an add* builder") {
    val c = code(configMap)
    assert(c.contains("def addLabels"), c)
  }

  test("list property gets an add* builder") {
    val m =
      DataModel.SubResource("PodList", "test.pkg", None, Seq(containerItems))
    assert(code(m).contains("def addItems"), code(m))
  }

  test("non-collection properties do not get an add* builder") {
    val c = code(configMap)
    assert(!c.contains("def addName"), c)
    assert(!c.contains("def addNamespace"), c)
  }

  // ---- required vs optional types ----

  test("required property has plain type without Option") {
    val c = code(configMap)
    assert(c.contains("name : String"), c)
    assert(!c.contains("name : Option[String]"), c)
  }

  test("optional property has Option type") {
    val c = code(configMap)
    assert(c.contains("namespace : Option[String]"), c)
  }

  // ---- write routing ----

  test("write puts Resource into managed directory") {
    val (managed, unmanaged) =
      withScg(scg => ObjectGenerator.write(scg)(configMap))
    assert(
      managed.contains("ConfigMap"),
      s"expected ConfigMap in managed: ${managed.keys}"
    )
    assert(!unmanaged.contains("ConfigMap"))
  }

  test("write puts SubResource into managed directory") {
    val (managed, _) = withScg(scg => ObjectGenerator.write(scg)(objectMeta))
    assert(
      managed.contains("ObjectMeta"),
      s"expected ObjectMeta in managed: ${managed.keys}"
    )
  }

  test("write puts Primitive into unmanaged directory") {
    val (managed, unmanaged) =
      withScg(scg => ObjectGenerator.write(scg)(intOrString))
    assert(
      unmanaged.contains("IntOrString"),
      s"expected IntOrString in unmanaged: ${unmanaged.keys}"
    )
    assert(!managed.contains("IntOrString"))
  }
}
