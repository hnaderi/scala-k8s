package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object EncoderGenerator {
  def apply(model: DataModel): String = {
    val tpe = s"${model.pkg.replace('-', '_')}.${model.name}"

    val (ps, hasAdditionalEnc, hasAdditionalDec) = model match {
      case r: Resource      => (r.properties, true, false)
      case sr: SubResource  => (sr.properties, false, false)
      case mr: MetaResource => (mr.properties, false, true)
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
    implicit def encoder[T](implicit builder : Builder[T]) : Encoder[$tpe, T] = new Encoder[$tpe, T] {
        def apply(o: $tpe) : T = {
          val obj = ObjectWriter[T]()
          obj
$encoderFields
            .build
        }
    }"""
  }
}
