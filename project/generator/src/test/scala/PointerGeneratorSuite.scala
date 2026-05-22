package dev.hnaderi.k8s.generator

import munit.FunSuite
import ModelFixtures._

class PointerGeneratorSuite extends FunSuite {

  private def generate(
      models: Seq[DataModel]
  ): (Map[String, String], Map[String, String]) =
    withScg(scg => PointerGenerator.write(scg)(models))

  // ---- pointer class generation ----

  test("Resource produces a *Pointer case class") {
    val (managed, _) = generate(Seq(configMap))
    val name = "ConfigMapPointer"
    assert(
      managed.contains(name),
      s"expected $name in managed: ${managed.keys}"
    )
    assert(managed(name).contains(s"final case class $name"), managed(name))
  }

  test("SubResource produces a *Pointer case class") {
    val (managed, _) = generate(Seq(objectMeta))
    val name = "ObjectMetaPointer"
    assert(
      managed.contains(name),
      s"expected $name in managed: ${managed.keys}"
    )
  }

  test("MetaResource produces a *Pointer case class") {
    val (managed, _) = generate(Seq(watchEvent))
    assert(managed.contains("WatchEventPointer"), s"keys: ${managed.keys}")
  }

  test("Primitive does not produce a pointer class") {
    val (managed, _) = generate(Seq(intOrString))
    assert(
      !managed.exists(_._1.endsWith("Pointer")),
      s"Primitive should not generate a pointer class, got: ${managed.keys}"
    )
  }

  // ---- pointer field type mapping ----

  test("Primitive-typed field maps to Pointer.Plain") {
    val model = DataModel.SubResource(
      "Foo",
      "test.pkg",
      None,
      Seq(
        ModelProperty(
          "count",
          ModelPropertyType.Primitive("Int"),
          required = true
        )
      )
    )
    val (managed, _) = generate(Seq(model))
    assert(
      managed("FooPointer").contains("Pointer.Plain[Int]"),
      managed("FooPointer")
    )
  }

  test("Ref-typed field maps to XPointer for non-blackbox type") {
    val model = DataModel.SubResource("Foo", "test.pkg", None, Seq(metadataRef))
    val (managed, _) = generate(Seq(model))
    val code = managed("FooPointer")
    assert(code.contains("ObjectMetaPointer"), code)
  }

  test("Ref to a Primitive (blackbox) maps to Pointer.Plain") {
    val blackboxRef = ModelProperty(
      "value",
      ModelPropertyType.Ref("io.k8s.apimachinery.pkg.util.intstr.IntOrString"),
      required = true
    )
    val model = DataModel.SubResource("Foo", "test.pkg", None, Seq(blackboxRef))
    val (managed, _) = generate(Seq(intOrString, model))
    val code = managed("FooPointer")
    assert(code.contains("Pointer.Plain"), code)
    assert(!code.contains("IntOrStringPointer"), code)
  }

  test("Object-typed field maps to MapPointer") {
    val model = DataModel.SubResource("Foo", "test.pkg", None, Seq(labels))
    val (managed, _) = generate(Seq(model))
    assert(
      managed("FooPointer").contains("MapPointer[String]"),
      managed("FooPointer")
    )
  }

  test("List-typed field maps to ListPointer") {
    val model =
      DataModel.SubResource("Foo", "test.pkg", None, Seq(containerItems))
    val (managed, _) = generate(Seq(model))
    assert(
      managed("FooPointer").contains("ListPointer[String]"),
      managed("FooPointer")
    )
  }

  // ---- pointer path ----

  test("Pointer case class has default empty PointerPath") {
    val (managed, _) = generate(Seq(configMap))
    assert(
      managed("ConfigMapPointer").contains(
        "currentPath: PointerPath = PointerPath()"
      ),
      managed("ConfigMapPointer")
    )
  }

  // ---- Instances file ----

  test("Instances file is generated") {
    val (managed, _) = generate(Seq(configMap))
    assert(managed.contains("Instances"), s"keys: ${managed.keys}")
  }

  test("Instances contains Pointable implicit for each non-primitive model") {
    val (managed, _) = generate(Seq(configMap, objectMeta, intOrString))
    val instances = managed("Instances")
    assert(instances.contains("ConfigMap"), instances)
    assert(instances.contains("ObjectMeta"), instances)
    assert(!instances.contains("IntOrString"), instances)
  }

  // ---- hyphen in package name ----

  test(
    "hyphen in model package is replaced with underscore in generated package"
  ) {
    val model = DataModel.SubResource(
      "Foo",
      "io.k8s.kube-aggregator.v1",
      None,
      Seq(nameStr)
    )
    val (managed, _) = generate(Seq(model))
    val code = managed("FooPointer")
    assert(code.contains("package io.k8s.kube_aggregator.v1"), code)
    assert(!code.contains("kube-aggregator"), code)
  }
}
