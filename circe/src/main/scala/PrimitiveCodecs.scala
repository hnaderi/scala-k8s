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

package dev.hnaderi.k8s
package circe

import io.circe._
import io.circe.syntax._
import io.k8s.api.apiserverinternal.v1alpha1.StorageVersionSpec
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.CustomResourceSubresourceStatus
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSON
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaProps
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray
import io.k8s.apimachinery.pkg.api.resource.Quantity
import io.k8s.apimachinery.pkg.apis.meta.v1.FieldsV1
import io.k8s.apimachinery.pkg.apis.meta.v1.MicroTime
import io.k8s.apimachinery.pkg.apis.meta.v1.Patch
import io.k8s.apimachinery.pkg.apis.meta.v1.Time
import io.k8s.apimachinery.pkg.runtime.RawExtension
import io.k8s.apimachinery.pkg.util.intstr.IntOrString

import InternalCodecs.io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSONSchemaPropsEncoder
import InternalCodecs.io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSONSchemaPropsDecoder

private[circe] object PrimitiveCodecs {
  implicit val intOrStringEncoder: Encoder[IntOrString] = {
    case IntOrString.IntValue(i)    => i.asJson
    case IntOrString.StringValue(s) => s.asJson
  }
  implicit val intOrStringDecoder: Decoder[IntOrString] =
    Decoder[String].map(IntOrString(_)).or(Decoder[Int].map(IntOrString(_)))

  implicit val timeEncoder: Encoder[Time] = t => t.value.asJson
  implicit val timeDecoder: Decoder[Time] = Decoder[String].map(Time(_))

  implicit val microTimeEncoder: Encoder[MicroTime] = t => t.value.asJson
  implicit val microTimeDecoder: Decoder[MicroTime] =
    Decoder[String].map(MicroTime(_))

  implicit val quantityEncoder: Encoder[Quantity] = t => t.value.asJson
  implicit val quantityDecoder: Decoder[Quantity] =
    Decoder[String].map(Quantity(_))

  implicit val apiextensionsJSONEncoder: Encoder[JSON] = j => j.value.asJson
  implicit val apiextensionsJSONDecoder: Decoder[JSON] =
    Decoder[String].map(JSON(_))

  implicit val jsonSchemaPropsOrBoolEncoder: Encoder[JSONSchemaPropsOrBool] = {
    case JSONSchemaPropsOrBool.BoolValue(b)  => b.asJson
    case JSONSchemaPropsOrBool.PropsValue(p) => p.asJson
  }
  implicit val jsonSchemaPropsOrBoolDecoder: Decoder[JSONSchemaPropsOrBool] =
    Decoder[JSONSchemaProps]
      .map(JSONSchemaPropsOrBool(_))
      .or(Decoder[Boolean].map(JSONSchemaPropsOrBool(_)))

  implicit val jsonSchemaPropsOrArrayEncoder
      : Encoder[JSONSchemaPropsOrArray] = {
    case JSONSchemaPropsOrArray.SingleValue(p)    => p.asJson
    case JSONSchemaPropsOrArray.MutipleValues(ps) => ps.asJson
  }
  implicit val jsonSchemaPropsOrArrayDecoder: Decoder[JSONSchemaPropsOrArray] =
    Decoder[Seq[JSONSchemaProps]]
      .map(JSONSchemaPropsOrArray.MutipleValues(_))
      .or(Decoder[JSONSchemaProps].map(JSONSchemaPropsOrArray(_)))

  implicit val jsonSchemaPropsOrStringArrayEncoder
      : Encoder[JSONSchemaPropsOrStringArray] = {
    case JSONSchemaPropsOrStringArray.PropsValue(p)  => p.asJson
    case JSONSchemaPropsOrStringArray.StringList(ss) => ss.asJson
  }
  implicit val jsonSchemaPropsOrStringArrayDecoder
      : Decoder[JSONSchemaPropsOrStringArray] =
    Decoder[JSONSchemaProps]
      .map(JSONSchemaPropsOrStringArray(_))
      .or(Decoder[Seq[String]].map(JSONSchemaPropsOrStringArray(_)))

  //
  // THESE ARE STUB TYPES THAT ARE POSSIBLY NOT IMPLEMENTED COMPLETELY
  //
  private def stubEnc[T]: Encoder[T] = _ => Json.Null

  implicit val customResourceSubresourceStatusEncoder
      : Encoder[CustomResourceSubresourceStatus] = stubEnc
  implicit val customResourceSubresourceStatusDecoder
      : Decoder[CustomResourceSubresourceStatus] =
    Decoder.const(CustomResourceSubresourceStatus())
  implicit val apimachineryv1FieldsV1Encoder: Encoder[FieldsV1] = stubEnc
  implicit val apimachineryv1FieldsV1Decoder: Decoder[FieldsV1] =
    Decoder.const(FieldsV1())
  implicit val apimachineryv1PatchEncoder: Encoder[Patch] = stubEnc
  implicit val apimachineryv1PatchDecoder: Decoder[Patch] =
    Decoder.const(Patch())
  implicit val apimachineryruntimeRawExtensionEncoder: Encoder[RawExtension] =
    stubEnc
  implicit val apimachineryruntimeRawExtensionDecoder: Decoder[RawExtension] =
    Decoder.const(RawExtension())
  implicit val apiserverinternalv1alpha1StorageVersionSpecEncoder
      : Encoder[StorageVersionSpec] = stubEnc
  implicit val apiserverinternalv1alpha1StorageVersionSpecDecoder
      : Decoder[StorageVersionSpec] = Decoder.const(StorageVersionSpec())
}
