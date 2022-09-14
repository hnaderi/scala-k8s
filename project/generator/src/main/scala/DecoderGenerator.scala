package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object DecoderGenerator {
  private implicit val kindOrdering: Ordering[Kind] =
    Ordering.by(k => (k.group, k.kind, k.version))
  private implicit val dataModelOrdering: Ordering[DataModel] = Ordering.by {
    case _: DataModel.Primitive    => 0
    case _: DataModel.SubResource  => 1
    case _: DataModel.Resource     => 2
    case _: DataModel.MetaResource => 3
  }

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

  def resources(all: Seq[DataModel]) = {}
}
