package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object CirceCodecGenerator {

  private def typeName(d: DataModel) = s"${d.pkg.replace('-', '_')}.${d.name}"
  private def codecName(d: DataModel) =
    s"${d.pkg.replace('-', '_').replace('.', '_')}${d.name}"

  private implicit val kindOrdering: Ordering[Kind] =
    Ordering.by(k => (k.group, k.kind, k.version))

  private object BaseCodecs {
    def apply(objs: Seq[DataModel]) = s"""package dev.hnaderi.k8s
package circe

import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._

object codecs {
${objs.map(body).mkString("\n")}
}
"""
    private val body: CodeGenerator = {
      case o: Resource     => resource(o)
      case o: SubResource  => subResource(o)
      case o: MetaResource => metaResource(o)
      case o: Primitive    => primitive(o)
    }

    private def encoderFieldFor(p: ModelProperty) =
      s""""${p.name}" -> o.${p.fieldName}.asJson"""

    private def encoderBody(ps: Seq[ModelProperty]) =
      ps.map(encoderFieldFor).mkString(",\n    ")

    private def encoderFor(d: Resource) =
      s""" implicit lazy val ${codecName(d)}Encoder : Encoder[${typeName(
          d
        )}] = Encoder.instance(o =>
  Json.obj(
    ${encoderBody(d.properties)},
    "kind" -> o.kind.asJson,
    "apiVersion" -> o.apiVersion.asJson
  )
)"""

    private val resource: CodeGeneratorFor[Resource] = r =>
      s"""  implicit lazy val ${codecName(r)}Decoder : Decoder[${typeName(
          r
        )}] = deriveDecoder
  ${encoderFor(r)}"""

    private val subResource: CodeGeneratorFor[SubResource] = r =>
      s"""  private implicit lazy val ${codecName(r)} : Codec[${typeName(
          r
        )}] = deriveCodec"""
    private val metaResource: CodeGeneratorFor[MetaResource] = r =>
      s"""  private implicit lazy val ${codecName(r)} : Codec[${typeName(
          r
        )}] = ???"""
    private val primitive: CodeGeneratorFor[Primitive] = r =>
      s"""  private implicit lazy val ${codecName(r)} : Codec[${typeName(
          r
        )}] = ???"""
  }
  private object KObjectCodec {
    def apply(res: Seq[DataModel]) = {
      val sorted = res.collect { case r: Resource => r }.sortBy(_.kind)

      s"""package dev.hnaderi.k8s
package circe

import io.circe._
import io.circe.syntax._
import codecs._
import cats.implicits._

object codecs2 {
${resourceEncoder(sorted)}
${resourceDecoder(sorted)}
}"""
    }

    private def groupDecoderName(group: String) =
      s"group${group.replace('.', '_')}Decoder"

    private def groupDecoders(sorted: Seq[Resource]) = {
      def matchers(rs: Seq[Resource]) = rs
        .map(r =>
          s"""    case ("${r.kind.kind}", "${r.kind.version}") => codecs.${codecName(
              r
            )}Decoder.widen"""
        )
        .mkString("\n")

      sorted
        .groupBy(_.kind.group)
        .map { case (group, rs) =>
          val groupCodec = groupDecoderName(group)
          s"""  private val $groupCodec : (String, String) => Decoder[KObject]  = {
${matchers(rs)}
    case (kind, version) => Decoder.failedWithMessage(s"Unknown kubernetes object: group: $group, kind: $$kind, version: $$version")
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
          s"""     case "$r" => c.as(${groupDecoderName(
              r
            )}(rk.kind, rk.version))"""
        }
        .mkString("\n")
      s"""  ${groupDecoders(sorted)}
  implicit val resourceDecoder : Decoder[KObject] = (c: HCursor) => c.as[ResourceKind].flatMap { rk =>
    rk.group match {
$matchers
      case unknown => c.as(Decoder.failedWithMessage(s"Unknown kubernetes group id: $$unknown"))
  }
}
"""
    }
    private def resourceEncoder(sorted: Seq[Resource]) = {
      val matchers = sorted
        .map(r => s"""    case o : ${typeName(r)} => o.asJson""")
        .mkString("\n")
      s"""  implicit val resourceEncoder : Encoder[KObject] = Encoder.instance{
$matchers
}"""
    }
  }

  def write(scg: SourceCodeGenerator)(models: Iterable[DataModel]) = {
    val mSeq = models.toSeq
    scg.unmanaged("", "BaseCodecs").write(BaseCodecs(mSeq))
    scg.unmanaged("", "KObjectCodecs").write(KObjectCodec(mSeq))
  }
}

// val a :Encoder[Int]= Encoder.instance(i=> Json.obj(
//                                         ""-> i.asJson,
//                                         "a"-> i.asJson,
//                                       ))
// val c = Decoder.
// val b: Decoder[Int]= (c:HCursor)=> for {
//   _ <- c.as[Int]
// }yield 1
