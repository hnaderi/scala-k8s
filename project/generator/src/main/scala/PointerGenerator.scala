package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object PointerGenerator {
  private def pkgName(model: DataModel) = model.pkg.replace('-', '_')

  private def apply(model: DataModel, blackboxTypes: Set[String]): String = {
    import model.name

    val ps = model match {
      case r: Resource      => r.properties
      case sr: SubResource  => sr.properties
      case mr: MetaResource => mr.properties
      case _                => return ""
    }

    val indent = " " * 10
    val tpe = model.fullName

    def pathFor(p: ModelProperty) = {
      val name = p.name.replaceAll("\\$", "\\$\\$")
      s"""currentPath / s"$name""""
    }
    def pointerFor(p: ModelProperty) = p.typeName match {
      case ModelPropertyType.Primitive(name) =>
        s"""Pointer.Plain[$name] = Pointer.Plain(${pathFor(p)})"""
      case ModelPropertyType.Ref(name) if blackboxTypes.contains(name) =>
        s"""Pointer.Plain[$name] = Pointer.Plain(${pathFor(p)})"""
      case ModelPropertyType.Ref(name) =>
        val ptrType = s"${name}Pointer"
        s""" $ptrType = $ptrType(${pathFor(p)})"""
      case ModelPropertyType.Object(valueType) =>
        s"""MapPointer[$valueType] = MapPointer(${pathFor(p)})"""
      case ModelPropertyType.List(valueType) =>
        s"""ListPointer[$valueType] = ListPointer(${pathFor(p)})"""
    }
    def fieldFor(p: ModelProperty) =
      s"""  def ${p.fieldName} : ${pointerFor(p)}"""
    val fields = model.properties.map(fieldFor).mkString("\n")
    val ptrName = s"${model.name}Pointer"

    s"""package ${pkgName(model)}

import dev.hnaderi.k8s.client._

/** Pointer for ${model.name} */
final case class $ptrName(currentPath: PointerPath = PointerPath()) extends Pointer[$tpe] {
$fields
}

"""
  }

  private def instances(models: Iterable[DataModel]): String = {
    def pointable(model: DataModel) = {
      val tpe = model.fullName
      val ptr = s"${tpe}Pointer"
      val instName = tpe.replace('.', '_').replace('-', '_')

      s"""  implicit lazy val $instName : Pointable[$tpe, $ptr] = Pointable($ptr(_))"""
    }
    val pointables = models.map(pointable).mkString("\n")

    s"""package dev.hnaderi.k8s.client

private[client] trait PointerInstances {
$pointables
}
"""
  }

  def write(scg: SourceCodeGenerator)(models: Iterable[DataModel]) = {
    val blackboxTypes = models.collect { case p: Primitive =>
      p.fullName
    }.toSet
    val m = models.collect {
      case r: Resource      => r
      case sr: SubResource  => sr
      case mr: MetaResource => mr
    }.toList

    m.foreach(model =>
      scg
        .managed(s"pointers.${model.pkg}", s"${model.name}Pointer")
        .write(PointerGenerator(model, blackboxTypes))
    )
    scg
      .managed("pointers", "Instances")
      .write(PointerGenerator.instances(m))
  }
}
