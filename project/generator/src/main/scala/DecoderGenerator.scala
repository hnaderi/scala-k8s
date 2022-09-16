package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object DecoderGenerator {
  def apply(model: DataModel): String = {
    import model.name

    val (ps, extraFields) = model match {
      case r: Resource      => (r.properties, false)
      case sr: SubResource  => (sr.properties, false)
      case mr: MetaResource => (mr.properties, true)
      case _                => return ""
    }

    val indent = " " * 10

    val fields =
      ps.map(_.fieldName) ++ (if (extraFields) Seq("apiVersion", "kind")
                              else Nil)
    val constructFields = fields.map(f => s"$f = $f").mkString(s",\n$indent")

    val extraReads =
      if (extraFields)
        Seq(
          """apiVersion <- obj.read[String]("apiVersion")""",
          """kind <- obj.read[String]("kind")"""
        )
      else Nil

    val reads = extraReads ++ ps.map(p =>
      if (p.required)
        s"""${p.fieldName} <- obj.read[${p.typeName.name}]("${p.name}")"""
      else s"""${p.fieldName} <- obj.readOpt[${p.typeName.name}]("${p.name}")"""
    )

    val fieldReads = reads.mkString(s"\n$indent")

    s"""
    implicit def decoderOf[T : Reader] : Decoder[T, $name] = new Decoder[T, $name] {
      def apply(t: T): Either[String, $name] = for {
          obj <- ObjectReader(t)
$indent$fieldReads
      } yield $name (
$indent$constructFields
        )
    }"""
  }

  private object KObjectCodec {
    private implicit val kindOrdering: Ordering[Kind] =
      Ordering.by(k => (k.group, k.kind, k.version))
    private implicit val dataModelOrdering: Ordering[DataModel] = Ordering.by {
      case _: DataModel.Primitive    => 0
      case _: DataModel.SubResource  => 1
      case _: DataModel.Resource     => 2
      case _: DataModel.MetaResource => 3
    }

    def apply(res: Seq[DataModel]) = {
      val sorted = res.collect { case r: Resource => r }.sortBy(_.kind)

      s"""package dev.hnaderi.k8s

import utils._

private[k8s] object ResourceCodecs {
${resourceDecoder(sorted)}
}"""
    }

    private def typeName(d: DataModel) = s"${d.pkg.replace('-', '_')}.${d.name}"
    private def groupDecoderName(group: String) =
      s"group${group.replace('.', '_')}Decoder"

    private def groupDecoders(sorted: Seq[Resource]) = {
      def matchers(rs: Seq[Resource]) = rs
        .map { r =>
          val name = typeName(r)
          s"""    case ("${r.kind.kind}", "${r.kind.version}") => Decoder[T, $name].apply(t)"""
        }
        .mkString("\n")

      sorted
        .groupBy(_.kind.group)
        .map { case (group, rs) =>
          val groupCodec = groupDecoderName(group)
          s"""
  private def $groupCodec[T : Reader](t: T) : (String, String) => Either[String, KObject]  = {
${matchers(rs)}
    case (kind, version) => Left(s"Unknown kubernetes object: group: $group, kind: $$kind, version: $$version")
  }"""
        }
        .mkString("\n")

    }
    private def resourceDecoder(sorted: Seq[Resource]) = {
      val matchers = sorted
        .map(_.kind.group)
        .distinct
        .sorted
        .map { r =>
          val name = groupDecoderName(r)
          s"""        case "$r" => $name(t).apply(kind, version)"""
        }
        .mkString("\n")
      s"""  ${groupDecoders(sorted)}
  implicit def resourceDecoder[T : Reader] : Decoder[T, KObject] = new Decoder[T, KObject]{
    def apply(t: T): Either[String, KObject] = for {
      obj <- ObjectReader(t)
      kind <- obj.read[String]("kind")
      apiVersion <- obj.read[String]("apiVersion")
      (group, secondPart) = apiVersion.splitAt(apiVersion.indexOf("/"))
      version = if(secondPart.startsWith("/")) secondPart.tail else secondPart
      res <- group match {
$matchers
        case unknown => Left(s"Unknown kubernetes group id: $$unknown")
      }
    } yield res
  }
"""
    }
  }

  def resources(scg: SourceCodeGenerator)(models: Iterable[DataModel]) = {
    scg.managed("", "KObjectDecoders").write(KObjectCodec(models.toSeq))
  }
}
