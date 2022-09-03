import DataModel.{Resource, SubResource, MetaResource, Primitive}

trait ObjectGenerator[T <: DataModel] {
  def print(t: T): String
  def write(t: T, scg: SourceCodeGenerator): Unit
}

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

  private def codecsFor(name: String) = s"""
//  import io.circe._
//  import io.circe.generic.semiauto._

//  implicit val encoder: Encoder[$name] = deriveEncoder
//  implicit val decoder: Decoder[$name] = deriveDecoder
"""

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

  private val resource: ObjectGenerator[Resource] =
    new ObjectGenerator[Resource] {
      override def print(t: Resource): String = {
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
      override def write(t: Resource, scg: SourceCodeGenerator): Unit =
        scg.managed(t.pkg, t.name).write(print(t))
    }

  private val metaResource: ObjectGenerator[MetaResource] =
    new ObjectGenerator[MetaResource] {
      override def print(t: MetaResource): String = {
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
      override def write(t: MetaResource, scg: SourceCodeGenerator): Unit =
        scg.managed(t.pkg, t.name).write(print(t))
    }

  private val subResource: ObjectGenerator[SubResource] =
    new ObjectGenerator[SubResource] {
      override def print(t: SubResource): String = {
        import t._
        s"""${t.header()}
final case class $name(
  ${printProps(properties)}
) {
${builderMethods(name, properties)}
}
"""
      }
      override def write(t: SubResource, scg: SourceCodeGenerator): Unit =
        scg.managed(t.pkg, t.name).write(print(t))
    }

  private val primitive: ObjectGenerator[Primitive] =
    new ObjectGenerator[Primitive] {
      override def print(t: Primitive): String = {
        import t._
        s"""${t.header()}
trait $name
object $name {

}
"""
      }
      override def write(t: Primitive, scg: SourceCodeGenerator): Unit =
        scg.unmanaged(t.pkg, t.name).write(print(t))
    }

  def write(scg: SourceCodeGenerator)(obj: DataModel) = obj match {
    case o: Resource     => resource.write(o, scg)
    case o: SubResource  => subResource.write(o, scg)
    case o: MetaResource => metaResource.write(o, scg)
    case o: Primitive    => primitive.write(o, scg)
  }
  def print(obj: DataModel) = obj match {
    case o: Resource     => resource.print(o)
    case o: SubResource  => subResource.print(o)
    case o: MetaResource => metaResource.print(o)
    case o: Primitive    => primitive.print(o)
  }
}
