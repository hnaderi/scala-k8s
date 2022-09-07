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

package dev.hnaderi.k8s.cookbook

import dev.hnaderi.k8s.implicits._
import io.k8s.api.core.v1.ContainerPort
import io.k8s.api.core.v1.ServicePort
import io.k8s.api.networking.v1.HTTPIngressPath
import io.k8s.api.networking.v1.HTTPIngressRuleValue
import io.k8s.api.networking.v1.IngressBackend
import io.k8s.api.networking.v1.IngressRule
import io.k8s.api.networking.v1.IngressServiceBackend
import io.k8s.apimachinery.pkg.util.intstr.IntOrString

final case class ServiceDefinition(
    name: String,
    port: Int,
    targetPort: Option[Int] = None,
    protocol: Option[String] = None,
    public: Option[PublicServiceDefinition] = None
) {
  def containerPort: ContainerPort =
    ContainerPort(containerPort = port, name = name, protocol = protocol)
  def servicePort: ServicePort = ServicePort(
    port = port,
    name = name,
    targetPort = targetPort.map(IntOrString(_)),
    protocol = protocol
  )

  def ingressRule: Option[IngressRule] = public
    .filter(_ => protocol.forall(_.toLowerCase == "http"))
    .map(defs =>
      IngressRule(
        host = defs.host,
        http = HTTPIngressRuleValue(
          defs.paths.map(p =>
            HTTPIngressPath(
              IngressBackend(service = IngressServiceBackend(name)),
              pathType = p.pathType,
              path = p.path
            )
          )
        )
      )
    )
}

final case class PublicServiceDefinition(
    paths: Seq[PathDefinition],
    host: Option[String] = None
)
object PublicServiceDefinition {
  def apply(paths: PathDefinition*): PublicServiceDefinition =
    PublicServiceDefinition(paths)
}

final case class PathDefinition(
    path: String,
    pathType: String = "Prefix"
)
