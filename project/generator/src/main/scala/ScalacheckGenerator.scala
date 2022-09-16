package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

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
        case (d: MetaResource, ps) =>
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

  private def isRecursive(d: DataModel) = d.name == "JSONSchemaProps"

  def write(scg: SourceCodeGenerator)(data: Seq[DataModel]) = {
    val primitives = data.collect {
      case o: Primitive                => o
      case other if isRecursive(other) => other
    }
    val other = data.collect {
      case o: Resource if !isRecursive(o)     => (o, o.properties)
      case o: SubResource if !isRecursive(o)  => (o, o.properties)
      case o: MetaResource if !isRecursive(o) => (o, o.properties)
    }
    val kobjs = data.collect { case r: Resource => r }
    scg.managed("", "NonPrimitiveGenerators").write(printOther(other))
    scg.unmanaged("", "PrimitiveGenerators").write(print(primitives))
    scg.managed("", "KObjectGenerators").write(printKObjects(kobjs))
  }
}
