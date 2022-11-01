/*
 * Copyright 2022 Hossein Naderi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.hnaderi.k8s.scalacheck

import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSON
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaProps
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray
import io.k8s.apimachinery.pkg.util.intstr.IntOrString
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

import Arbitrary.arbitrary

private[scalacheck] trait PrimitiveGenerators { self: NonPrimitiveGenerators =>
  private[scalacheck] implicit def arbSeq[T: Arbitrary]: Arbitrary[Seq[T]] =
    Arbitrary(
      Gen.choose(0, 3).flatMap(n => Gen.listOfN(n, Arbitrary.arbitrary[T]))
    )
  private[scalacheck] implicit def arbMap[T: Arbitrary]
      : Arbitrary[Map[String, T]] = Arbitrary(
    Gen
      .choose(0, 3)
      .flatMap(n =>
        Gen.mapOfN(
          n,
          for {
            k <- Gen.alphaNumStr
            v <- arbitrary[T]
          } yield (k, v)
        )
      )
  )

  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_api_resourceQuantity
      : Arbitrary[io.k8s.apimachinery.pkg.api.resource.Quantity] =
    Arbitrary(Gen.resultOf(io.k8s.apimachinery.pkg.api.resource.Quantity(_)))

  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_runtimeRawExtension
      : Arbitrary[io.k8s.apimachinery.pkg.runtime.RawExtension] =
    Arbitrary(Gen.const(io.k8s.apimachinery.pkg.runtime.RawExtension()))

  implicit lazy val arbitrary_io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1CustomResourceSubresourceStatus
      : Arbitrary[
        io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.CustomResourceSubresourceStatus
      ] = Arbitrary(
    Gen.const(
      io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1
        .CustomResourceSubresourceStatus()
    )
  )
  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_apis_meta_v1Time
      : Arbitrary[io.k8s.apimachinery.pkg.apis.meta.v1.Time] =
    Arbitrary(Gen.resultOf(io.k8s.apimachinery.pkg.apis.meta.v1.Time(_)))

  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_apis_meta_v1MicroTime
      : Arbitrary[io.k8s.apimachinery.pkg.apis.meta.v1.MicroTime] =
    Arbitrary(Gen.resultOf(io.k8s.apimachinery.pkg.apis.meta.v1.MicroTime(_)))

  implicit lazy val arbitrary_io_k8s_api_apiserverinternal_v1alpha1StorageVersionSpec
      : Arbitrary[io.k8s.api.apiserverinternal.v1alpha1.StorageVersionSpec] =
    Arbitrary(
      Gen.const(io.k8s.api.apiserverinternal.v1alpha1.StorageVersionSpec())
    )

  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_util_intstrIntOrString
      : Arbitrary[io.k8s.apimachinery.pkg.util.intstr.IntOrString] = Arbitrary(
    Gen.oneOf(
      Arbitrary.arbitrary[String].map(IntOrString(_)),
      Arbitrary.arbitrary[Int].map(IntOrString(_))
    )
  )

  implicit lazy val arbitrary_io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSON
      : Arbitrary[JSON] = Arbitrary(Gen.resultOf(JSON(_)))

  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_apis_meta_v1FieldsV1
      : Arbitrary[io.k8s.apimachinery.pkg.apis.meta.v1.FieldsV1] =
    Arbitrary(Gen.const(io.k8s.apimachinery.pkg.apis.meta.v1.FieldsV1()))

  private def genJSONSchemaPropsOrArray(
      jsp: JSONSchemaProps
  ): Gen[JSONSchemaPropsOrArray] =
    Gen.oneOf(
      Gen.const(JSONSchemaPropsOrArray(jsp)),
      Gen.listOf(Gen.const(jsp)).map(JSONSchemaPropsOrArray(_))
    )

  private def genJSONSchemaPropsOrBool(
      jsp: JSONSchemaProps
  ): Gen[JSONSchemaPropsOrBool] =
    Gen.oneOf(
      Gen.const(JSONSchemaPropsOrBool(jsp)),
      arbitrary[Boolean].map(JSONSchemaPropsOrBool(_))
    )

  private def genJSONSchemaPropsOrStringArray(
      jsp: JSONSchemaProps
  ): Gen[JSONSchemaPropsOrStringArray] =
    Gen.oneOf(
      Gen.const(JSONSchemaPropsOrStringArray(jsp)),
      arbitrary[Seq[String]].map(JSONSchemaPropsOrStringArray(_))
    )

  private def dependenciesMap(
      jsp: JSONSchemaProps
  ): Gen[Map[String, JSONSchemaPropsOrStringArray]] = Gen.mapOf {
    for {
      k <- Gen.alphaNumStr
      v <- genJSONSchemaPropsOrStringArray(jsp)
    } yield (k, v)
  }

  implicit lazy val arbitrary_io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSONSchemaProps
      : Arbitrary[JSONSchemaProps] = Arbitrary(jsonSchemaProps)

  private def jsonSchemas(jsp: JSONSchemaProps): Gen[List[JSONSchemaProps]] =
    for {
      n <- Gen.choose(0, 3)
    } yield List.fill(n)(jsp)

  private def jsonSchemaMap(
      jsp: JSONSchemaProps
  ): Gen[Map[String, JSONSchemaProps]] =
    Gen
      .choose(0, 3)
      .flatMap(n =>
        Gen.mapOfN(
          n,
          Gen.alphaStr.map((_, jsp))
        )
      )

  private def jsonSchemaProps: Gen[JSONSchemaProps] =
    Gen.recursive[JSONSchemaProps](recurse =>
      Gen.choose(1, 10).flatMap { n =>
        if (n > 7) recurse.map(Some(_)).flatMap(join)
        else join(None)
      }
    )

  private def opt[T](
      jsp: Option[JSONSchemaProps],
      g: JSONSchemaProps => Gen[T]
  ): Gen[Option[T]] =
    jsp match {
      case None        => Gen.const(None)
      case Some(value) => g(value).map(Some(_))
    }

  private def join(jsp: Option[JSONSchemaProps]): Gen[JSONSchemaProps] =
    for {
      exclusiveMaximum <- arbitrary[Option[Boolean]]
      format <- arbitrary[Option[String]]
      ref <- arbitrary[Option[String]]
      nullable <- arbitrary[Option[Boolean]]
      `x-kubernetes-map-type` <- arbitrary[Option[String]]
      pattern <- arbitrary[Option[String]]
      description <- arbitrary[Option[String]]
      anyOf <- opt(jsp, jsonSchemas)
      `x-kubernetes-list-type` <- arbitrary[Option[String]]
      patternProperties <- opt(jsp, jsonSchemaMap)
      items <- opt(jsp, genJSONSchemaPropsOrArray)
      additionalItems <- opt(jsp, genJSONSchemaPropsOrBool)
      maxProperties <- arbitrary[Option[Int]]
      maxItems <- arbitrary[Option[Int]]
      `x-kubernetes-int-or-string` <- arbitrary[Option[Boolean]]
      `x-kubernetes-embedded-resource` <- arbitrary[Option[Boolean]]
      maximum <- arbitrary[Option[Double]]
      multipleOf <- arbitrary[Option[Double]]
      id <- arbitrary[Option[String]]
      properties <- opt(jsp, jsonSchemaMap)
      exclusiveMinimum <- arbitrary[Option[Boolean]]
      `x-kubernetes-validations` <- arbitrary[Option[Seq[
        io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.ValidationRule
      ]]]
      `enum` <- arbitrary[Option[Seq[JSON]]]
      `x-kubernetes-preserve-unknown-fields` <- arbitrary[Option[Boolean]]
      additionalProperties <- opt(jsp, genJSONSchemaPropsOrBool)
      default <- arbitrary[Option[JSON]]
      minItems <- arbitrary[Option[Int]]
      not <- Gen.const(jsp)
      definitions <- opt(jsp, jsonSchemaMap)
      minLength <- arbitrary[Option[Int]]
      `x-kubernetes-list-map-keys` <- arbitrary[Option[Seq[String]]]
      title <- arbitrary[Option[String]]
      minimum <- arbitrary[Option[Double]]
      `type` <- arbitrary[Option[String]]
      required <- arbitrary[Option[Seq[String]]]
      example <- arbitrary[Option[JSON]]
      schema <- arbitrary[Option[String]]
      oneOf <- opt(jsp, jsonSchemas)
      uniqueItems <- arbitrary[Option[Boolean]]
      minProperties <- arbitrary[Option[Int]]
      dependencies <- opt(jsp, dependenciesMap)
      externalDocs <- arbitrary[Option[
        io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.ExternalDocumentation
      ]]
      maxLength <- arbitrary[Option[Int]]
      allOf <- opt(jsp, jsonSchemas)
    } yield JSONSchemaProps(
      exclusiveMaximum = exclusiveMaximum,
      format = format,
      ref = ref,
      nullable = nullable,
      `x-kubernetes-map-type` = `x-kubernetes-map-type`,
      pattern = pattern,
      description = description,
      anyOf = anyOf,
      `x-kubernetes-list-type` = `x-kubernetes-list-type`,
      patternProperties = patternProperties,
      items = items,
      additionalItems = additionalItems,
      maxProperties = maxProperties,
      maxItems = maxItems,
      `x-kubernetes-int-or-string` = `x-kubernetes-int-or-string`,
      `x-kubernetes-embedded-resource` = `x-kubernetes-embedded-resource`,
      maximum = maximum,
      multipleOf = multipleOf,
      id = id,
      properties = properties,
      exclusiveMinimum = exclusiveMinimum,
      `x-kubernetes-validations` = `x-kubernetes-validations`,
      `enum` = `enum`,
      `x-kubernetes-preserve-unknown-fields` =
        `x-kubernetes-preserve-unknown-fields`,
      additionalProperties = additionalProperties,
      default = default,
      minItems = minItems,
      not = not,
      definitions = definitions,
      minLength = minLength,
      `x-kubernetes-list-map-keys` = `x-kubernetes-list-map-keys`,
      title = title,
      minimum = minimum,
      `type` = `type`,
      required = required,
      example = example,
      schema = schema,
      oneOf = oneOf,
      uniqueItems = uniqueItems,
      minProperties = minProperties,
      dependencies = dependencies,
      externalDocs = externalDocs,
      maxLength = maxLength,
      allOf = allOf
    )
}
