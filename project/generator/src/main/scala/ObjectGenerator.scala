package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object ObjectGenerator {
  private implicit class HeaderWriter(val obj: DataModel) extends AnyVal {
    def header(imports: String*): String = {
      val sanitizedPkg = obj.pkg.replace('-', '_')

      s"""package $sanitizedPkg

${imports.map(s => s"import $s\n").mkString}
${Utils.generateDescription(obj.description)}"""
    }
  }

  def printProps: Seq[ModelProperty] => String =
    _.map(_.asParam).mkString(",\n  ")

  private def builderMethod(className: String, prop: ModelProperty): String = {
    val capName =
      prop.dashToCamelName.take(1).toUpperCase() + prop.dashToCamelName.drop(1)
    val value = if (prop.required) "value" else "Some(value)"
    import prop.fieldName
    import prop.typeName

    def result = {
      val construct = if (typeName.isArray) "" else ".toMap"
      if (prop.required) s"$fieldName ++ newValues"
      else s"Some($fieldName.fold(newValues$construct)(_ ++ newValues))"
    }

    val helpers = typeName match {
      case ModelPropertyType.Object(valueType) =>
        s"""
  def add$capName(newValues: (String, $valueType)*) : $className = copy($fieldName = $result)
"""
      case ModelPropertyType.List(valueType) =>
        s"""
  def add$capName(newValues: $valueType*) : $className = copy($fieldName = $result)
"""
      case _ => ""
    }

    s"""  def with$capName(value: ${typeName.name}) : $className = copy($fieldName = $value)$helpers"""
  }
  private def builderMethods(
      className: String,
      props: Seq[ModelProperty]
  ): String = props
    .filterNot(_.isKindOrAPIVersion)
    .map(builderMethod(className, _))
    .mkString("\n")

  private val resource: CodeGeneratorFor[Resource] = { t =>
    import t._
    s"""${t.header("dev.hnaderi.k8s._")}
final case class $name(
  ${printProps(properties.filterNot(_.isKindOrAPIVersion))}
) extends ResourceKind {
   val group = "${kind.group}"
   val kind = "${kind.kind}"
   val version = "${kind.version}"

${builderMethods(name, properties)}
}
"""
  }

  private val metaResource: CodeGeneratorFor[MetaResource] = { t =>
    import t._

    val supportedKinds = kinds
      .map(k =>
        s"""    ResourceKind("${k.group}", "${k.kind}", "${k.version}")"""
      )
      .mkString(",\n")

    s"""${t.header("dev.hnaderi.k8s._")}

sealed abstract case class $name(
  ${printProps(properties)}
) extends ResourceKind

object $name {
  def apply(
    _group: String,
    _kind: String,
    _version: String,
    ${printProps(properties)}
  ) : $name = new $name(
   ${properties.map(_.name).map(n => s"      $n = $n").mkString(",\n")}
) {
   val group = _group
   val kind = _kind
   val version = _version
}
  val knownKinds = Seq(
$supportedKinds
  )
}
"""
  }

  private val subResource: CodeGeneratorFor[SubResource] = { t =>
    import t._
    s"""${t.header()}
final case class $name(
  ${printProps(properties)}
) {
${builderMethods(name, properties)}
}
"""
  }

  private val primitive: CodeGeneratorFor[Primitive] = { t =>
    import t._
    s"""${t.header()}
trait $name
object $name {

}
"""
  }

  val print: CodeGenerator = {
    case o: Resource     => resource(o)
    case o: SubResource  => subResource(o)
    case o: MetaResource => metaResource(o)
    case o: Primitive    => primitive(o)
  }

  def write(scg: SourceCodeGenerator)(data: DataModel) = data match {
    case p: Primitive =>
      scg.unmanaged(pkg = p.pkg, name = p.name).write(print(p))
    case other =>
      scg.managed(pkg = other.pkg, name = other.name).write(print(other))
  }
}
