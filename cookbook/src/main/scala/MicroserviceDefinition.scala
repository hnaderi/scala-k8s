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
package cookbook

import dev.hnaderi.k8s._
import dev.hnaderi.k8s.implicits._
import io.k8s.api.apps.v1.Deployment
import io.k8s.api.apps.v1.DeploymentSpec
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.LocalObjectReference
import io.k8s.api.core.v1.PodSpec
import io.k8s.api.core.v1.PodTemplateSpec
import io.k8s.api.core.v1.Service
import io.k8s.api.core.v1.ServiceSpec
import io.k8s.api.networking.v1.Ingress
import io.k8s.api.networking.v1.IngressSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.LabelSelector
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

final case class MicroserviceDefinition(
    name: String,
    version: String,
    image: String,
    namespace: String = "default",
    imagePullSecret: Seq[String] = Nil,
    environments: Seq[EnvironmentDefinition] = Nil,
    services: Seq[ServiceDefinition] = Nil
) {
  private val commonLabels =
    Seq(Labels.version(version), Labels.managedBy("dev.hnaderi.sbtk8s"))

  private val metadata =
    ObjectMeta(
      name = name,
      namespace = namespace,
      labels = Map(
        Labels.name(name)
      ) ++ commonLabels
    )

  private def service: List[Service] =
    services.headOption.fold(List.empty[Service])(_ =>
      List(
        Service(
          metadata = metadata,
          spec = ServiceSpec(
            selector = Map(Labels.name(name)),
            ports = services.map(_.servicePort)
          )
        )
      )
    )

  private def ingress: List[Ingress] =
    services.headOption.fold(List.empty[Ingress])(_ =>
      List(
        Ingress(
          metadata = metadata,
          spec = IngressSpec(rules = services.flatMap(_.ingressRule))
        )
      )
    )

  private def configObject = environments
    .flatMap(_.configMap)
    .map(_.mapMetadata(_.addLabels(commonLabels: _*)))
    .toList
  private def secretObject = environments
    .flatMap(_.secret)
    .map(_.mapMetadata(_.addLabels(commonLabels: _*)))
    .toList
  private def deployment = List(
    Deployment(
      metadata = metadata,
      spec = DeploymentSpec(
        selector = LabelSelector(matchLabels = Map(Labels.name(name))),
        template = PodTemplateSpec(
          metadata = metadata,
          spec = PodSpec(
            containers = Seq(
              Container(
                name = name,
                image = image,
                ports = services.map(_.containerPort),
                env = environments.flatMap(_.env),
                volumeMounts = environments.flatMap(_.volMount)
              )
            ),
            imagePullSecrets =
              imagePullSecret.map(s => LocalObjectReference(s)),
            volumes = environments.flatMap(_.vol)
          )
        )
      )
    )
  )

  def build: Seq[KObject] =
    service ::: ingress ::: configObject ::: secretObject ::: deployment

}
