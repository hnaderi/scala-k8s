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
package apis.corev1

import io.k8s.api.core.v1.Pod
import io.k8s.api.core.v1.PodList

object PodAPI
    extends CoreV1.NamespacedResourceAPI[Pod, PodList](
      "pods"
    ) {
  case class Logs(
      namespace: String,
      name: String,
      container: Option[String] = None,
      follow: Boolean = false,
      previous: Boolean = false,
      sinceSeconds: Option[Long] = None,
      sinceTime: Option[String] = None,
      timestamps: Boolean = false,
      tailLines: Option[Long] = None,
      limitBytes: Option[Long] = None
  ) extends PodLogsRequest(
        PodAPI.urlFor(namespace, name) + "/log",
        container = container,
        follow = follow,
        previous = previous,
        sinceSeconds = sinceSeconds,
        sinceTime = sinceTime,
        timestamps = timestamps,
        tailLines = tailLines,
        limitBytes = limitBytes
      )

  case class Exec(
      namespace: String,
      name: String,
      command: Seq[String],
      container: Option[String] = None,
      tty: Boolean = false,
      stdinEnabled: Boolean = false,
      stdoutEnabled: Boolean = true,
      stderrEnabled: Boolean = true
  ) extends PodExecRequest(
        PodAPI.urlFor(namespace, name) + "/exec",
        command = command,
        container = container,
        tty = tty,
        stdinEnabled = stdinEnabled,
        stdoutEnabled = stdoutEnabled,
        stderrEnabled = stderrEnabled
      )
}

final case class PodAPI(namespace: String)
    extends PodAPI.NamespacedAPIBuilders {
  def logs(
      name: String,
      container: Option[String] = None,
      follow: Boolean = false,
      previous: Boolean = false,
      sinceSeconds: Option[Long] = None,
      sinceTime: Option[String] = None,
      timestamps: Boolean = false,
      tailLines: Option[Long] = None,
      limitBytes: Option[Long] = None
  ): PodAPI.Logs = PodAPI.Logs(
    namespace,
    name,
    container = container,
    follow = follow,
    previous = previous,
    sinceSeconds = sinceSeconds,
    sinceTime = sinceTime,
    timestamps = timestamps,
    tailLines = tailLines,
    limitBytes = limitBytes
  )

  def exec(
      name: String,
      command: Seq[String],
      container: Option[String] = None,
      tty: Boolean = false,
      stdinEnabled: Boolean = false,
      stdoutEnabled: Boolean = true,
      stderrEnabled: Boolean = true
  ): PodAPI.Exec = PodAPI.Exec(
    namespace,
    name,
    command,
    container = container,
    tty = tty,
    stdinEnabled = stdinEnabled,
    stdoutEnabled = stdoutEnabled,
    stderrEnabled = stderrEnabled
  )
}

object ClusterPodAPI extends PodAPI.ClusterwideAPIBuilders
