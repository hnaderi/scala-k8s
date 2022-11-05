package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object EncoderGenerator {
  def apply(model: DataModel): String = {
    val tpe = s"${model.pkg.replace('-', '_')}.${model.name}"

    val (ps, hasAdditionalEnc) = model match {
      case r: Resource      => (r.properties, true)
      case sr: SubResource  => (sr.properties, false)
      case mr: MetaResource => (mr.properties, true)
      case _                => return ""
    }

    def encoderFieldFor(p: ModelProperty) =
      s""""${p.name}"""" -> s"o.${p.fieldName}"

    val additionalFields =
      if (hasAdditionalEnc)
        Seq(
          "\"kind\"" -> "o.kind",
          "\"apiVersion\"" -> "o.apiVersion"
        )
      else Nil

    val encoderFields =
      (ps.map(encoderFieldFor) ++ additionalFields)
        .map { case (field, value) => s"            .write($field, $value)" }
        .mkString("\n")

    s"""
    implicit val encoder : Encoder[$tpe] = new Encoder[$tpe] {
        def apply[T : Builder](o: $tpe) : T = {
          val obj = ObjectWriter[T]()
          obj
$encoderFields
            .build
        }
    }"""
  }
}
