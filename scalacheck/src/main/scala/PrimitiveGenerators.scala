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

import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaProps
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool
import io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray
import io.k8s.apimachinery.pkg.util.intstr.IntOrString
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

import Arbitrary.arbitrary

private[scalacheck] trait PrimitiveGenerators { self: NonPrimitiveGenerators =>
  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_api_resourceQuantity
      : Arbitrary[io.k8s.apimachinery.pkg.api.resource.Quantity] = Arbitrary(
    Gen.resultOf(io.k8s.apimachinery.pkg.api.resource.Quantity(_))
  )
  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_runtimeRawExtension
      : Arbitrary[io.k8s.apimachinery.pkg.runtime.RawExtension] = Arbitrary(
    Gen.const(io.k8s.apimachinery.pkg.runtime.RawExtension())
  )
  implicit lazy val arbitrary_io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSONSchemaPropsOrBool
      : Arbitrary[
        io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool
      ] = Arbitrary(
    Gen.oneOf(
      arbitrary[JSONSchemaProps].map(JSONSchemaPropsOrBool(_)),
      arbitrary[Boolean].map(JSONSchemaPropsOrBool(_))
    )
  )
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
      : Arbitrary[io.k8s.apimachinery.pkg.apis.meta.v1.Time] = Arbitrary(
    Gen.resultOf(io.k8s.apimachinery.pkg.apis.meta.v1.Time(_))
  )
  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_apis_meta_v1MicroTime
      : Arbitrary[io.k8s.apimachinery.pkg.apis.meta.v1.MicroTime] = Arbitrary(
    Gen.resultOf(io.k8s.apimachinery.pkg.apis.meta.v1.MicroTime(_))
  )
  implicit lazy val arbitrary_io_k8s_api_apiserverinternal_v1alpha1StorageVersionSpec
      : Arbitrary[io.k8s.api.apiserverinternal.v1alpha1.StorageVersionSpec] =
    Arbitrary(
      Gen.const(io.k8s.api.apiserverinternal.v1alpha1.StorageVersionSpec())
    )
  implicit lazy val arbitrary_io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSONSchemaPropsOrStringArray
      : Arbitrary[
        io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray
      ] = Arbitrary(
    Gen.oneOf(
      arbitrary[JSONSchemaProps].map(JSONSchemaPropsOrStringArray(_)),
      arbitrary[Seq[String]].map(JSONSchemaPropsOrStringArray(_))
    )
  )
  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_apis_meta_v1Patch
      : Arbitrary[io.k8s.apimachinery.pkg.apis.meta.v1.Patch] = Arbitrary(
    Gen.const(io.k8s.apimachinery.pkg.apis.meta.v1.Patch())
  )
  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_util_intstrIntOrString
      : Arbitrary[io.k8s.apimachinery.pkg.util.intstr.IntOrString] = Arbitrary(
    Gen.oneOf(
      Arbitrary.arbitrary[String].map(IntOrString(_)),
      Arbitrary.arbitrary[Int].map(IntOrString(_))
    )
  )
  implicit lazy val arbitrary_io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSON
      : Arbitrary[
        io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSON
      ] = Arbitrary(
    Gen.resultOf(
      io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSON(_)
    )
  )
  implicit lazy val arbitrary_io_k8s_apimachinery_pkg_apis_meta_v1FieldsV1
      : Arbitrary[io.k8s.apimachinery.pkg.apis.meta.v1.FieldsV1] = Arbitrary(
    Gen.const(io.k8s.apimachinery.pkg.apis.meta.v1.FieldsV1())
  )
  implicit lazy val arbitrary_io_k8s_apiextensions_apiserver_pkg_apis_apiextensions_v1JSONSchemaPropsOrArray
      : Arbitrary[
        io.k8s.apiextensions_apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray
      ] = Arbitrary(
    Gen.oneOf(
      arbitrary[JSONSchemaProps].map(JSONSchemaPropsOrArray(_)),
      arbitrary[Seq[JSONSchemaProps]].map(JSONSchemaPropsOrArray(_))
    )
  )
}
