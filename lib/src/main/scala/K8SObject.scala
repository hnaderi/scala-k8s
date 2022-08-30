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

package dev.hnaderi.sbtk8s

import io.circe.Json
import io.circe.syntax.EncoderOps
import io.k8s.api._
import io.k8s.api.apps.v1.DaemonSetSpec
import io.k8s.api.apps.v1.DeploymentSpec
import io.k8s.api.apps.v1.ReplicaSetSpec
import io.k8s.api.apps.v1.StatefulSetSpec
import io.k8s.api.core.v1.ServiceSpec
import io.k8s.api.networking.v1.IngressSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

sealed trait K8SObject {
  def buildManifest: Json
}

object K8SObject {
  case class Deployment(metadata: Metadata, spec: DeploymentSpec)
      extends K8SObject {
    def build = apps.v1.Deployment(
      apiVersion = Some("apps/v1"),
      kind = Some("Deployment"),
      metadata = metadata.build,
      spec = spec
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): Deployment =
      copy(metadata = metadata)
    def withSpec(spec: DeploymentSpec): Deployment = copy(spec = spec)
  }
  case class StatefulSet(metadata: Metadata, spec: StatefulSetSpec)
      extends K8SObject {
    def build = apps.v1.StatefulSet(
      apiVersion = Some("apps/v1"),
      kind = Some("StatefulSet"),
      metadata = metadata.build,
      spec = spec
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): StatefulSet =
      copy(metadata = metadata)
    def withSpec(spec: StatefulSetSpec): StatefulSet = copy(spec = spec)
  }
  case class DaemonSet(metadata: Metadata, spec: DaemonSetSpec)
      extends K8SObject {
    def build = apps.v1.DaemonSet(
      apiVersion = Some("apps/v1"),
      kind = Some("DaemonSet"),
      metadata = metadata.build,
      spec = spec
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): DaemonSet =
      copy(metadata = metadata)
    def withSpec(spec: DaemonSetSpec): DaemonSet = copy(spec = spec)
  }
  case class ReplicaSet(metadata: Metadata, spec: ReplicaSetSpec)
      extends K8SObject {
    def build = apps.v1.ReplicaSet(
      apiVersion = Some("apps/v1"),
      kind = Some("ReplicaSet"),
      metadata = metadata.build,
      spec = spec
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): ReplicaSet =
      copy(metadata = metadata)
    def withSpec(spec: ReplicaSetSpec): ReplicaSet = copy(spec = spec)
  }
  case class Service(metadata: Metadata, spec: ServiceSpec) extends K8SObject {
    def build = core.v1.Service(
      apiVersion = Some("v1"),
      kind = Some("Service"),
      metadata = metadata.build,
      spec = spec
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): Service =
      copy(metadata = metadata)
    def withSpec(spec: ServiceSpec): Service = copy(spec = spec)
  }

  case class ConfigMap(
      metadata: Metadata,
      binaryData: Map[String, Data] = Map.empty,
      data: Map[String, Data] = Map.empty,
      immutable: Option[Boolean] = None
  ) extends K8SObject {
    def build = core.v1.ConfigMap(
      apiVersion = Some("v1"),
      kind = Some("ConfigMap"),
      metadata = metadata.build,
      binaryData = binaryData.mapValues(_.getBase64Content),
      data = data.mapValues(_.getContent),
      immutable = immutable
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): ConfigMap =
      copy(metadata = metadata)
    def addData(values: (String, Data)*): ConfigMap =
      copy(data = data ++ values)
    def addBinaryData(values: (String, Data)*): ConfigMap =
      copy(binaryData = binaryData ++ values)

    def toImmutable: ConfigMap = copy(immutable = Some(true))
    def toMutable: ConfigMap = copy(immutable = Some(false))
  }
  case class Secret(
      metadata: Metadata,
      data: Map[String, Data] = Map.empty,
      stringData: Map[String, Data] = Map.empty,
      immutable: Option[Boolean] = None,
      `type`: Option[String] = None
  ) extends K8SObject {
    def build = core.v1.Secret(
      apiVersion = Some("v1"),
      kind = Some("Secret"),
      metadata = metadata.build,
      data = data.mapValues(_.getBase64Content),
      stringData = stringData.mapValues(_.getContent),
      immutable = immutable,
      `type` = `type`
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): Secret =
      copy(metadata = metadata)
    def addStringData(values: (String, Data)*): Secret =
      copy(stringData = stringData ++ values)
    def addData(
        values: (String, Data)*
    ): Secret = copy(data = data ++ values)
    def toImmutable: Secret = copy(immutable = Some(true))
    def toMutable: Secret = copy(immutable = Some(false))
    def withType(tpe: String): Secret = copy(`type` = Some(tpe))
  }

  case class Ingress(metadata: Metadata, spec: IngressSpec) extends K8SObject {
    def build = networking.v1.Ingress(
      apiVersion = Some("networking.k8s.io/v1"),
      kind = Some("Ingress"),
      metadata = metadata.build,
      spec = spec
    )
    def buildManifest: Json = build.asJson

    def withMetadata(metadata: Metadata): Ingress =
      copy(metadata = metadata)
    def withSpec(spec: IngressSpec): Ingress = copy(spec = spec)
  }

  final case class Metadata(
      name: String,
      namespace: String = "default",
      annotations: Option[Map[String, String]] = None,
      labels: Option[Map[String, String]] = None
  ) {
    private[K8SObject] def build = ObjectMeta(
      name = name,
      namespace = namespace,
      annotations = annotations,
      labels = labels
    )
  }
}
