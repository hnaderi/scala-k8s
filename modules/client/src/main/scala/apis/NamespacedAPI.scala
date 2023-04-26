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

package dev.hnaderi.k8s.client

import dev.hnaderi.k8s.utils.Decoder
import dev.hnaderi.k8s.utils.Encoder

import scala.concurrent.duration.FiniteDuration
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions

trait NamespacedAPI {
  protected def namespace: String
}

abstract class APIGroupAPI(base: String) {
  case object resources extends APIResourceListingRequest(base)

  abstract class ResourceAPIBase[
      RES: Decoder,
      COL: Decoder
  ](resourceName: String) {
    protected val clusterwideUrl = s"$base/$resourceName"

    case class ListAll(
        allowWatchBookmarks: Option[Boolean] = None,
        continue: Option[String] = None,
        fieldSelector: List[String] = Nil,
        labelSelector: List[String] = Nil,
        limit: Option[Int] = None,
        resourceVersion: Option[String] = None,
        resourceVersionMatch: Option[String] = None,
        timeout: Option[FiniteDuration] = None
    ) extends ListingRequest[RES, COL](clusterwideUrl)

    trait ClusterwideAPIBuilders {
      def list(
          allowWatchBookmarks: Option[Boolean] = None,
          continue: Option[String] = None,
          fieldSelector: List[String] = Nil,
          labelSelector: List[String] = Nil,
          limit: Option[Int] = None,
          resourceVersion: Option[String] = None,
          resourceVersionMatch: Option[String] = None,
          timeout: Option[FiniteDuration] = None
      ): ListAll = ListAll(
        allowWatchBookmarks = allowWatchBookmarks,
        continue = continue,
        fieldSelector = fieldSelector,
        labelSelector = labelSelector,
        limit = limit,
        resourceVersion = resourceVersion,
        resourceVersionMatch = resourceVersionMatch,
        timeout = timeout
      )
    }
  }

  abstract class NamespacedResourceAPI[
      RES: Decoder: Encoder,
      COL: Decoder
  ](resourceName: String)
      extends ResourceAPIBase[RES, COL](resourceName) {
    protected def urlFor(namespace: String, name: String) =
      s"${baseUrlIn(namespace)}/$name"
    protected def baseUrlIn(namespace: String) =
      s"$base/namespaces/${namespace}/$resourceName"

    case class ListInNamespace(namespace: String)
        extends ListingRequest[RES, COL](baseUrlIn(namespace))
    case class Create(
        namespace: String,
        configmap: RES,
        dryRun: Option[String] = None,
        fieldManager: Option[String] = None,
        fieldValidation: Option[String] = None
    ) extends CreateRequest(
          baseUrlIn(namespace),
          configmap,
          dryRun = dryRun,
          fieldManager = fieldManager,
          fieldValidation = fieldValidation
        )
    case class Get(namespace: String, name: String)
        extends GetRequest[RES](urlFor(namespace, name))
    case class Delete(
        namespace: String,
        name: String,
        options: Option[DeleteOptions] = None,
        dryRun: Option[String] = None,
        gracePeriodSeconds: Option[Int] = None,
        propagationPolicy: Option[String] = None
    ) extends DeleteRequest[RES](
          urlFor(namespace, name),
          options,
          dryRun = dryRun,
          gracePeriodSeconds = gracePeriodSeconds,
          propagationPolicy = propagationPolicy
        )
    case class DeleteCollection(
        namespace: String,
        options: Option[DeleteOptions] = None,
        continue: Option[String] = None,
        dryRun: Option[String] = None,
        fieldSelector: List[String] = Nil,
        gracePeriodSeconds: Option[Int] = None,
        labelSelector: List[String] = Nil,
        limit: Option[Int] = None,
        propagationPolicy: Option[String] = None,
        resourceVersion: Option[String] = None,
        resourceVersionMatch: Option[String] = None,
        timeoutSeconds: Option[Int] = None
    ) extends DeleteCollectionRequest[RES](
          baseUrlIn(namespace),
          options,
          continue = continue,
          dryRun = dryRun,
          fieldSelector = fieldSelector,
          gracePeriodSeconds = gracePeriodSeconds,
          labelSelector = labelSelector,
          limit = limit,
          propagationPolicy = propagationPolicy,
          resourceVersion = resourceVersion,
          resourceVersionMatch = resourceVersionMatch,
          timeoutSeconds = timeoutSeconds
        )
    case class Replace(
        name: String,
        namespace: String,
        body: RES,
        dryRun: Option[String] = None,
        fieldManager: Option[String] = None,
        fieldValidation: Option[String] = None
    ) extends ReplaceRequest(
          urlFor(namespace, name),
          body,
          dryRun = dryRun,
          fieldManager = fieldManager,
          fieldValidation = fieldValidation
        )
    case class ServerSideApply(
        name: String,
        namespace: String,
        body: RES,
        fieldManager: String,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        force: Option[Boolean] = None
    ) extends PartialUpdateRequest[RES, RES](
          body,
          PatchType.ServerSide,
          url = urlFor(namespace, name),
          dryRun = dryRun,
          fieldValidation = fieldValidation,
          fieldManager = Some(fieldManager),
          force = force
        )

    case class GenericPatch[BODY: Encoder](
        name: String,
        namespace: String,
        body: BODY,
        patch: PatchType,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        fieldManager: Option[String] = None,
        force: Option[Boolean] = None
    ) extends PartialUpdateRequest[BODY, RES](
          body,
          patch,
          url = urlFor(namespace, name),
          dryRun = dryRun,
          fieldValidation = fieldValidation,
          fieldManager = fieldManager,
          force = force
        )

    trait NamespacedAPIBuilders extends NamespacedAPI {
      def get(name: String): Get = Get(namespace, name)
      val list: ListInNamespace = ListInNamespace(namespace)
      def delete(
          name: String,
          options: Option[DeleteOptions] = None,
          dryRun: Option[String] = None,
          gracePeriodSeconds: Option[Int] = None,
          propagationPolicy: Option[String] = None
      ): Delete = Delete(
        namespace,
        name,
        options,
        dryRun = dryRun,
        gracePeriodSeconds = gracePeriodSeconds,
        propagationPolicy = propagationPolicy
      )

      def deleteAll(
          options: Option[DeleteOptions] = None,
          continue: Option[String] = None,
          dryRun: Option[String] = None,
          fieldSelector: List[String] = Nil,
          gracePeriodSeconds: Option[Int] = None,
          labelSelector: List[String] = Nil,
          limit: Option[Int] = None,
          propagationPolicy: Option[String] = None,
          resourceVersion: Option[String] = None,
          resourceVersionMatch: Option[String] = None,
          timeoutSeconds: Option[Int] = None
      ): DeleteCollection = DeleteCollection(
        namespace = namespace,
        options = options,
        continue = continue,
        dryRun = dryRun,
        fieldSelector = fieldSelector,
        gracePeriodSeconds = gracePeriodSeconds,
        labelSelector = labelSelector,
        limit = limit,
        propagationPolicy = propagationPolicy,
        resourceVersion = resourceVersion,
        resourceVersionMatch = resourceVersionMatch,
        timeoutSeconds = timeoutSeconds
      )

      def create(
          configmap: RES,
          dryRun: Option[String] = None,
          fieldManager: Option[String] = None,
          fieldValidation: Option[String] = None
      ): Create = Create(
        namespace,
        configmap,
        dryRun = dryRun,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation
      )
      def replace(
          name: String,
          configmap: RES,
          dryRun: Option[String] = None,
          fieldManager: Option[String] = None,
          fieldValidation: Option[String] = None
      ): Replace = Replace(
        name,
        namespace,
        configmap,
        dryRun = dryRun,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation
      )
      def serverSideApply(
          name: String,
          body: RES,
          fieldManager: String,
          dryRun: Option[String] = None,
          fieldValidation: Option[String] = None,
          force: Option[Boolean] = None
      ): ServerSideApply = ServerSideApply(
        name,
        namespace,
        body,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation,
        dryRun = dryRun,
        force = force
      )
      def jsonPatch[P <: Pointer[RES]](
          name: String,
          dryRun: Option[String] = None,
          fieldValidation: Option[String] = None,
          fieldManager: Option[String] = None,
          force: Option[Boolean] = None
      )(
          body: JsonPatch[RES, P]
      ): GenericPatch[JsonPatch[RES, P]] =
        GenericPatch[JsonPatch[RES, P]](
          name,
          namespace,
          body,
          patch = PatchType.JsonPatch,
          fieldManager = fieldManager,
          fieldValidation = fieldValidation,
          dryRun = dryRun,
          force = force
        )
      def patchRaw(
          name: String,
          dryRun: Option[String] = None,
          fieldValidation: Option[String] = None,
          fieldManager: Option[String] = None,
          force: Option[Boolean] = None
      )(
          body: JsonPatchRaw => JsonPatchRaw
      ): GenericPatch[JsonPatchRaw] = GenericPatch[JsonPatchRaw](
        name,
        namespace,
        body(JsonPatchRaw()),
        patch = PatchType.JsonPatch,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation,
        dryRun = dryRun,
        force = force
      )
      def patch(
          name: String,
          body: RES,
          patch: PatchType = PatchType.StrategicMerge,
          dryRun: Option[String] = None,
          fieldValidation: Option[String] = None,
          fieldManager: Option[String] = None,
          force: Option[Boolean] = None
      ): GenericPatch[RES] = GenericPatch[RES](
        name,
        namespace,
        body,
        patch,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation,
        dryRun = dryRun,
        force = force
      )
      def patchGeneric[T: Encoder](
          name: String,
          body: T,
          patch: PatchType = PatchType.StrategicMerge,
          dryRun: Option[String] = None,
          fieldValidation: Option[String] = None,
          fieldManager: Option[String] = None,
          force: Option[Boolean] = None
      ): GenericPatch[T] = GenericPatch[T](
        name,
        namespace,
        body,
        patch,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation,
        dryRun = dryRun,
        force = force
      )
    }
  }

  abstract class ClusterResourceAPI[
      RES: Decoder: Encoder,
      COL: Decoder
  ](resourceName: String)
      extends ResourceAPIBase[RES, COL](resourceName) {
    protected def urlFor(name: String) =
      s"$clusterwideUrl/$name"

    case class Get(name: String) extends GetRequest[RES](urlFor(name))

    case class Create(
        configmap: RES,
        dryRun: Option[String] = None,
        fieldManager: Option[String] = None,
        fieldValidation: Option[String] = None
    ) extends CreateRequest(
          clusterwideUrl,
          configmap,
          dryRun = dryRun,
          fieldManager = fieldManager,
          fieldValidation = fieldValidation
        )
    case class Delete(
        name: String,
        options: Option[DeleteOptions] = None,
        dryRun: Option[String] = None,
        gracePeriodSeconds: Option[Int] = None,
        propagationPolicy: Option[String] = None
    ) extends DeleteRequest[RES](
          urlFor(name),
          options,
          dryRun = dryRun,
          gracePeriodSeconds = gracePeriodSeconds,
          propagationPolicy = propagationPolicy
        )
    case class DeleteCollection(
        options: Option[DeleteOptions] = None,
        continue: Option[String] = None,
        dryRun: Option[String] = None,
        fieldSelector: List[String] = Nil,
        gracePeriodSeconds: Option[Int] = None,
        labelSelector: List[String] = Nil,
        limit: Option[Int] = None,
        propagationPolicy: Option[String] = None,
        resourceVersion: Option[String] = None,
        resourceVersionMatch: Option[String] = None,
        timeoutSeconds: Option[Int] = None
    ) extends DeleteCollectionRequest[RES](
          clusterwideUrl,
          options,
          continue = continue,
          dryRun = dryRun,
          fieldSelector = fieldSelector,
          gracePeriodSeconds = gracePeriodSeconds,
          labelSelector = labelSelector,
          limit = limit,
          propagationPolicy = propagationPolicy,
          resourceVersion = resourceVersion,
          resourceVersionMatch = resourceVersionMatch,
          timeoutSeconds = timeoutSeconds
        )
    case class Replace(
        name: String,
        body: RES,
        dryRun: Option[String] = None,
        fieldManager: Option[String] = None,
        fieldValidation: Option[String] = None
    ) extends ReplaceRequest(
          urlFor(name),
          body,
          dryRun = dryRun,
          fieldManager = fieldManager,
          fieldValidation = fieldValidation
        )
    case class ServerSideApply(
        name: String,
        body: RES,
        fieldManager: String,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        force: Option[Boolean] = None
    ) extends PartialUpdateRequest[RES, RES](
          body,
          PatchType.ServerSide,
          url = urlFor(name),
          dryRun = dryRun,
          fieldValidation = fieldValidation,
          fieldManager = Some(fieldManager),
          force = force
        )
    case class GenericPatch[BODY: Encoder](
        name: String,
        body: BODY,
        patch: PatchType,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        fieldManager: Option[String] = None,
        force: Option[Boolean] = None
    ) extends PartialUpdateRequest[BODY, RES](
          body,
          patch,
          url = urlFor(name),
          dryRun = dryRun,
          fieldValidation = fieldValidation,
          fieldManager = fieldManager,
          force = force
        )

    def get(name: String): Get = Get(name)
    def list(
        allowWatchBookmarks: Option[Boolean] = None,
        continue: Option[String] = None,
        fieldSelector: List[String] = Nil,
        labelSelector: List[String] = Nil,
        limit: Option[Int] = None,
        resourceVersion: Option[String] = None,
        resourceVersionMatch: Option[String] = None,
        timeout: Option[FiniteDuration] = None
    ): ListAll = ListAll(
      allowWatchBookmarks = allowWatchBookmarks,
      continue = continue,
      fieldSelector = fieldSelector,
      labelSelector = labelSelector,
      limit = limit,
      resourceVersion = resourceVersion,
      resourceVersionMatch = resourceVersionMatch,
      timeout = timeout
    )
    def delete(
        name: String,
        options: Option[DeleteOptions] = None,
        dryRun: Option[String] = None,
        gracePeriodSeconds: Option[Int] = None,
        propagationPolicy: Option[String] = None
    ): Delete = Delete(
      name,
      options,
      dryRun = dryRun,
      gracePeriodSeconds = gracePeriodSeconds,
      propagationPolicy = propagationPolicy
    )

    def deleteAll(
        options: Option[DeleteOptions] = None,
        continue: Option[String] = None,
        dryRun: Option[String] = None,
        fieldSelector: List[String] = Nil,
        gracePeriodSeconds: Option[Int] = None,
        labelSelector: List[String] = Nil,
        limit: Option[Int] = None,
        propagationPolicy: Option[String] = None,
        resourceVersion: Option[String] = None,
        resourceVersionMatch: Option[String] = None,
        timeoutSeconds: Option[Int] = None
    ): DeleteCollection = DeleteCollection(
      options = options,
      continue = continue,
      dryRun = dryRun,
      fieldSelector = fieldSelector,
      gracePeriodSeconds = gracePeriodSeconds,
      labelSelector = labelSelector,
      limit = limit,
      propagationPolicy = propagationPolicy,
      resourceVersion = resourceVersion,
      resourceVersionMatch = resourceVersionMatch,
      timeoutSeconds = timeoutSeconds
    )

    def create(
        configmap: RES,
        dryRun: Option[String] = None,
        fieldManager: Option[String] = None,
        fieldValidation: Option[String] = None
    ): Create = Create(
      configmap,
      dryRun = dryRun,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation
    )
    def replace(
        name: String,
        configmap: RES,
        dryRun: Option[String] = None,
        fieldManager: Option[String] = None,
        fieldValidation: Option[String] = None
    ): Replace = Replace(
      name,
      configmap,
      dryRun = dryRun,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation
    )
    def serverSideApply(
        name: String,
        body: RES,
        fieldManager: String,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        force: Option[Boolean] = None
    ): ServerSideApply = ServerSideApply(
      name,
      body,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation,
      dryRun = dryRun,
      force = force
    )
    def jsonPatch[P <: Pointer[RES]](
        name: String,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        fieldManager: Option[String] = None,
        force: Option[Boolean] = None
    )(
        body: JsonPatch[RES, P]
    ): GenericPatch[JsonPatch[RES, P]] =
      GenericPatch[JsonPatch[RES, P]](
        name,
        body,
        patch = PatchType.JsonPatch,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation,
        dryRun = dryRun,
        force = force
      )
    def patchRaw(
        name: String,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        fieldManager: Option[String] = None,
        force: Option[Boolean] = None
    )(
        body: JsonPatchRaw => JsonPatchRaw
    ): GenericPatch[JsonPatchRaw] = GenericPatch[JsonPatchRaw](
      name,
      body(JsonPatchRaw()),
      patch = PatchType.JsonPatch,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation,
      dryRun = dryRun,
      force = force
    )
    def patch(
        name: String,
        body: RES,
        patch: PatchType = PatchType.StrategicMerge,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        fieldManager: Option[String] = None,
        force: Option[Boolean] = None
    ): GenericPatch[RES] = GenericPatch[RES](
      name,
      body,
      patch,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation,
      dryRun = dryRun,
      force = force
    )
    def patchGeneric[T: Encoder](
        name: String,
        body: T,
        patch: PatchType = PatchType.StrategicMerge,
        dryRun: Option[String] = None,
        fieldValidation: Option[String] = None,
        fieldManager: Option[String] = None,
        force: Option[Boolean] = None
    ): GenericPatch[T] = GenericPatch[T](
      name,
      body,
      patch,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation,
      dryRun = dryRun,
      force = force
    )

  }

}
