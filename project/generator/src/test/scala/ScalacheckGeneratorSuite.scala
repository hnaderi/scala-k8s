package dev.hnaderi.k8s.generator

import munit.FunSuite
import ModelFixtures._

class ScalacheckGeneratorSuite extends FunSuite {

  private def generate(
      models: Seq[DataModel]
  ): (Map[String, String], Map[String, String]) =
    withScg(scg => ScalacheckGenerator.write(scg)(models))

  private def manyProps(n: Int): Seq[ModelProperty] =
    (1 to n).map(i =>
      ModelProperty(
        s"field$i",
        ModelPropertyType.Primitive("String"),
        required = false,
        default = Some("None")
      )
    )

  // ---- output files ----

  test("generates NonPrimitiveGenerators in managed") {
    val (managed, _) = generate(Seq(configMap))
    assert(managed.contains("NonPrimitiveGenerators"), s"keys: ${managed.keys}")
  }

  test("generates KObjectGenerators in managed") {
    val (managed, _) = generate(Seq(configMap))
    assert(managed.contains("KObjectGenerators"), s"keys: ${managed.keys}")
  }

  test("generates PrimitiveGenerators in unmanaged") {
    val (_, unmanaged) = generate(Seq(intOrString))
    assert(
      unmanaged.contains("PrimitiveGenerators"),
      s"keys: ${unmanaged.keys}"
    )
  }

  // ---- content routing ----

  test("Primitive appears in PrimitiveGenerators") {
    val (_, unmanaged) = generate(Seq(intOrString))
    assert(
      unmanaged("PrimitiveGenerators").contains("IntOrString"),
      unmanaged("PrimitiveGenerators")
    )
  }

  test("Resource appears in NonPrimitiveGenerators") {
    val (managed, _) = generate(Seq(configMap))
    assert(
      managed("NonPrimitiveGenerators").contains("ConfigMap"),
      managed("NonPrimitiveGenerators")
    )
  }

  test("SubResource appears in NonPrimitiveGenerators") {
    val (managed, _) = generate(Seq(objectMeta))
    assert(
      managed("NonPrimitiveGenerators").contains("ObjectMeta"),
      managed("NonPrimitiveGenerators")
    )
  }

  test("MetaResource appears in NonPrimitiveGenerators") {
    val (managed, _) = generate(Seq(watchEvent))
    assert(
      managed("NonPrimitiveGenerators").contains("WatchEvent"),
      managed("NonPrimitiveGenerators")
    )
  }

  test("Resource appears in KObjectGenerators") {
    val (managed, _) = generate(Seq(configMap))
    assert(
      managed("KObjectGenerators").contains("ConfigMap"),
      managed("KObjectGenerators")
    )
  }

  test("SubResource does not appear in KObjectGenerators") {
    val (managed, _) = generate(Seq(objectMeta))
    assert(
      !managed("KObjectGenerators").contains("ObjectMeta"),
      managed("KObjectGenerators")
    )
  }

  test("Primitive does not appear in NonPrimitiveGenerators") {
    val (managed, _) = generate(Seq(intOrString))
    assert(
      !managed("NonPrimitiveGenerators").contains("IntOrString"),
      managed("NonPrimitiveGenerators")
    )
  }

  // ---- ctor strategy ----

  test("model with few fields uses Gen.resultOf") {
    val (managed, _) = generate(Seq(configMap))
    assert(
      managed("NonPrimitiveGenerators").contains("Gen.resultOf"),
      managed("NonPrimitiveGenerators")
    )
  }

  test("model with more than 22 fields uses for comprehension") {
    val large =
      DataModel.SubResource("BigModel", "test.pkg", None, manyProps(23))
    val (managed, _) = generate(Seq(large))
    val code = managed("NonPrimitiveGenerators")
    assert(
      code.contains("for {"),
      s"expected for comprehension for >22 fields:\n$code"
    )
    assert(!code.contains("Gen.resultOf"), code)
  }

  test("model with exactly 22 fields uses Gen.resultOf") {
    val exact =
      DataModel.SubResource("ExactModel", "test.pkg", None, manyProps(22))
    val (managed, _) = generate(Seq(exact))
    assert(
      managed("NonPrimitiveGenerators").contains("Gen.resultOf"),
      managed("NonPrimitiveGenerators")
    )
  }

  // ---- MetaResource ctor ----

  test(
    "MetaResource uses smallCtor accounting for kind and apiVersion params"
  ) {
    val (managed, _) = generate(Seq(watchEvent))
    val code = managed("NonPrimitiveGenerators")
    // WatchEvent has 2 properties + kind + apiVersion = 4 args in smallCtor
    assert(
      code.contains(
        "Gen.resultOf(io.k8s.apimachinery.pkg.apis.meta.v1.WatchEvent(_, _, _, _))"
      ),
      code
    )
  }

  // ---- JSONSchemaProps is recursive ----

  test(
    "JSONSchemaProps goes into PrimitiveGenerators as recursive special case"
  ) {
    val jsonSchema = DataModel.SubResource(
      "JSONSchemaProps",
      "io.k8s.apiextensions",
      None,
      Seq(nameStr, nsOpt)
    )
    val (_, unmanaged) = generate(Seq(jsonSchema))
    assert(
      unmanaged("PrimitiveGenerators").contains("JSONSchemaProps"),
      unmanaged("PrimitiveGenerators")
    )
  }

  test("JSONSchemaProps in PrimitiveGenerators uses for comprehension") {
    val jsonSchema = DataModel.SubResource(
      "JSONSchemaProps",
      "io.k8s.apiextensions",
      None,
      Seq(nameStr, nsOpt)
    )
    val (_, unmanaged) = generate(Seq(jsonSchema))
    assert(
      unmanaged("PrimitiveGenerators").contains("for {"),
      unmanaged("PrimitiveGenerators")
    )
  }
}
