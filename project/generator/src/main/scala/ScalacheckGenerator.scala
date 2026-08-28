package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, EmptyObject, Primitive}

object ScalacheckGenerator {
  private def arbName(data: DataModel) =
    "arbitrary_" + data.pkg.replace('-', '_').replace('.', '_') + data.name
  private def typeFor(data: DataModel) =
    data.pkg.replace('-', '_') + "." + data.name
  private def definitionFor(d: DataModel) =
    s"""  implicit lazy val ${arbName(d)}: Arbitrary[${typeFor(d)}]"""

  private def smallCtor(d: DataModel, fieldNum: Int): String = {
    val ctor = List.fill(fieldNum)("_").mkString(", ")
    s""" Arbitrary(Gen.resultOf(${typeFor(d)}($ctor)))"""
  }
  private def arbValue(parent: DataModel, field: ModelProperty) = {
    val pType = typeFor(parent)
    s"arbitrary[${field.fullTypename}]"
  }

  private def largeCtor(d: DataModel, ps: Seq[ModelProperty]): String = {
    val fields = ps
      .map(p => s"      ${p.fieldName} <- ${arbValue(d, p)}")
      .mkString("\n")
    val ctorArgs =
      ps.map(p => s"      ${p.fieldName} = ${p.fieldName}").mkString(",\n")
    val gen = s"""for {
$fields
    } yield ${typeFor(d)}(
$ctorArgs
    )"""

    s""" Arbitrary($gen)"""
  }

  private def print(data: Seq[DataModel]) = {
    val arbs = data
      .map { p =>
        val tpe = typeFor(p)
        val name = arbName(p)
        if (isRecursive(p))
          s"""  implicit lazy val $name: Arbitrary[$tpe] = ${largeCtor(
              p,
              p.properties
            )}"""
        else
          s"""  implicit lazy val $name: Arbitrary[$tpe] = ${smallCtor(p, 1)}"""
      }
      .mkString("\n")
    s"""package dev.hnaderi.k8s.scalacheck

import org.scalacheck.Arbitrary
import org.scalacheck.Gen

private[scalacheck] trait PrimitiveGenerators {
$arbs
}
"""
  }

  private def printOther(data: Seq[(DataModel, Seq[ModelProperty])]) = {
    val arbs = data
      .map {
        case (d: EmptyObject, _) =>
          s"${definitionFor(d)} = Arbitrary(Gen.const(${typeFor(d)}()))"
        case (d, ps) if selfRefs(d).nonEmpty => recursiveCtor(d, ps)
        case (d: MetaResource, ps)           =>
          s"${definitionFor(d)} = ${smallCtor(d, ps.size + 2)}"
        case (d, ps) if ps.size <= 22 =>
          s"${definitionFor(d)} = ${smallCtor(d, ps.size)}"
        case (d, ps) =>
          s"${definitionFor(d)} = ${largeCtor(d, ps)}"
      }
      .mkString("\n")
    s"""package dev.hnaderi.k8s.scalacheck

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

private[scalacheck] trait NonPrimitiveGenerators { self : PrimitiveGenerators =>
$arbs
}
"""
  }

  private def printKObjects(data: Seq[Resource]) = {

    s"""package dev.hnaderi.k8s
package scalacheck

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

private[scalacheck] trait KObjectGenerators { self : NonPrimitiveGenerators with PrimitiveGenerators =>
  implicit val arbitraryKObjects : Arbitrary[KObject] = Arbitrary(
    Gen.oneOf(
      ${data.map(typeFor).map(t => s"arbitrary[$t]").mkString(",\n      ")}
    )
  )
}
"""
  }

  /** Number of nested values a generator for a self recursive model produces
    * before it terminates.
    */
  private val maxRecursionDepth = 3

  private def genName(data: DataModel) =
    "gen_" + data.pkg.replace('-', '_').replace('.', '_') + data.name

  /** Properties of a model that refer back to the model itself. */
  private def selfRefs(d: DataModel): Seq[ModelProperty] = {
    val self = d.fullName
    d.properties.filter(_.typeName match {
      case ModelPropertyType.Ref(n)    => n == self
      case ModelPropertyType.List(n)   => n == self
      case ModelPropertyType.Object(n) => n == self
      case _                           => false
    })
  }

  /** A self reference can only be bounded if it is allowed to be absent; a
    * required one has no terminating value to generate.
    */
  private def isRecursive(d: DataModel) =
    d.name == "JSONSchemaProps" ||
      selfRefs(d).exists(p =>
        p.required && !p.typeName.isArray && !p.typeName.isObject
      )

  /** Generates a self recursive model by counting the nesting depth and
    * terminating the recursive properties once the limit is reached.
    */
  private def recursiveCtor(d: DataModel, ps: Seq[ModelProperty]): String = {
    val self = d.fullName
    val gen = genName(d)
    val nested = s"$gen(depth + 1)"

    def bounded(deeper: String, terminal: String) =
      s"if (depth >= $maxRecursionDepth) $terminal else $deeper"
    def optional(deeper: String) =
      bounded(s"Gen.option($deeper)", "Gen.const(None)")

    def valueGen(p: ModelProperty) = p.typeName match {
      case ModelPropertyType.Ref(n) if n == self  => optional(nested)
      case ModelPropertyType.List(n) if n == self =>
        val items = s"Gen.choose(0, 2).flatMap(Gen.listOfN(_, $nested))"
        if (p.required) bounded(items, s"Gen.const(Seq.empty[$n])")
        else optional(items)
      case ModelPropertyType.Object(n) if n == self =>
        val entries =
          s"Gen.choose(0, 2).flatMap(Gen.mapOfN(_, Gen.zip(Gen.alphaNumStr, $nested)))"
        if (p.required) bounded(entries, s"Gen.const(Map.empty[String, $n])")
        else optional(entries)
      case _ => s"arbitrary[${p.fullTypename}]"
    }

    val fields =
      ps.map(p => s"      ${p.fieldName} <- ${valueGen(p)}").mkString("\n")
    val ctorArgs =
      ps.map(p => s"      ${p.fieldName} = ${p.fieldName}").mkString(",\n")

    s"""${definitionFor(d)} = Arbitrary($gen(0))
  private def $gen(depth: Int): Gen[${typeFor(d)}] = for {
$fields
    } yield ${typeFor(d)}(
$ctorArgs
    )"""
  }

  def write(scg: SourceCodeGenerator)(data: Seq[DataModel]) = {
    val primitives = data.collect {
      case o: Primitive                => o
      case other if isRecursive(other) => other
    }
    val other = data.collect {
      case o: Resource if !isRecursive(o)     => (o, o.properties)
      case o: SubResource if !isRecursive(o)  => (o, o.properties)
      case o: MetaResource if !isRecursive(o) => (o, o.properties)
      case o: EmptyObject                     => (o, o.properties)
    }
    val kobjs = data.collect { case r: Resource => r }
    scg.managed("", "NonPrimitiveGenerators").write(printOther(other))
    scg.unmanaged("", "PrimitiveGenerators").write(print(primitives))
    scg.managed("", "KObjectGenerators").write(printKObjects(kobjs))
  }
}
