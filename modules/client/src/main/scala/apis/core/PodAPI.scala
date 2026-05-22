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
import io.k8s.api.policy.v1.Eviction

private[corev1] abstract class PodLogsRequest(
    url: String,
    container: Option[String] = None,
    follow: Boolean = false,
    previous: Boolean = false,
    sinceSeconds: Option[Long] = None,
    sinceTime: Option[String] = None,
    timestamps: Boolean = false,
    tailLines: Option[Long] = None,
    limitBytes: Option[Long] = None
) extends LinesRequest {
  private def params: Seq[(String, String)] = Seq(
    container.map("container" -> _),
    if (follow) Some("follow" -> "true") else None,
    if (previous) Some("previous" -> "true") else None,
    sinceSeconds.map("sinceSeconds" -> _.toString),
    sinceTime.map("sinceTime" -> _),
    if (timestamps) Some("timestamps" -> "true") else None,
    tailLines.map("tailLines" -> _.toString),
    limitBytes.map("limitBytes" -> _.toString)
  ).flatten

  override def lines[F[_]](client: StreamingClient[F]): F[String] =
    client.lines(url, params: _*)
}

private[corev1] abstract class PodExecRequest(
    url: String,
    command: Seq[String],
    container: Option[String] = None,
    tty: Boolean = false,
    stdinEnabled: Boolean = false,
    stdoutEnabled: Boolean = true,
    stderrEnabled: Boolean = true
) extends ExecRequest {
  private def params: Seq[(String, String)] =
    command.map("command" -> _) ++
      container.map("container" -> _) ++
      Seq(
        "stdout" -> stdoutEnabled.toString,
        "stderr" -> stderrEnabled.toString,
        "stdin" -> stdinEnabled.toString,
        "tty" -> tty.toString
      )

  override def exec[F[_]](client: ExecClient[F]): F[ExecInput] => F[ExecEvent] =
    client.exec(url, params: _*)
}

private[corev1] abstract class PodAttachRequest(
    url: String,
    container: Option[String] = None,
    tty: Boolean = false,
    stdinEnabled: Boolean = false,
    stdoutEnabled: Boolean = true,
    stderrEnabled: Boolean = true
) extends ExecRequest {
  private def params: Seq[(String, String)] =
    container.map("container" -> _).toSeq ++
      Seq(
        "stdout" -> stdoutEnabled.toString,
        "stderr" -> stderrEnabled.toString,
        "stdin" -> stdinEnabled.toString,
        "tty" -> tty.toString
      )

  override def exec[F[_]](client: ExecClient[F]): F[ExecInput] => F[ExecEvent] =
    client.exec(url, params: _*)
}

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

  case class GetStatus(namespace: String, name: String)
      extends GetRequest[Pod](PodAPI.urlFor(namespace, name) + "/status")
  case class ReplaceStatus(
      namespace: String,
      name: String,
      body: Pod,
      dryRun: Option[String] = None,
      fieldManager: Option[String] = None,
      fieldValidation: Option[String] = None
  ) extends ReplaceRequest[Pod](
        PodAPI.urlFor(namespace, name) + "/status",
        body,
        dryRun = dryRun,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation
      )
  case class Evict(
      namespace: String,
      name: String,
      eviction: Eviction = Eviction(),
      dryRun: Option[String] = None,
      fieldManager: Option[String] = None,
      fieldValidation: Option[String] = None
  ) extends CreateRequest[Eviction](
        PodAPI.urlFor(namespace, name) + "/eviction",
        eviction,
        dryRun = dryRun,
        fieldManager = fieldManager,
        fieldValidation = fieldValidation
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

  case class Attach(
      namespace: String,
      name: String,
      container: Option[String] = None,
      tty: Boolean = false,
      stdinEnabled: Boolean = false,
      stdoutEnabled: Boolean = true,
      stderrEnabled: Boolean = true
  ) extends PodAttachRequest(
        PodAPI.urlFor(namespace, name) + "/attach",
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

  def getStatus(name: String): PodAPI.GetStatus =
    PodAPI.GetStatus(namespace, name)
  def replaceStatus(
      name: String,
      body: Pod,
      dryRun: Option[String] = None,
      fieldManager: Option[String] = None,
      fieldValidation: Option[String] = None
  ): PodAPI.ReplaceStatus =
    PodAPI.ReplaceStatus(
      namespace,
      name,
      body,
      dryRun = dryRun,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation
    )
  def evict(
      name: String,
      eviction: Eviction = Eviction(),
      dryRun: Option[String] = None,
      fieldManager: Option[String] = None,
      fieldValidation: Option[String] = None
  ): PodAPI.Evict =
    PodAPI.Evict(
      namespace,
      name,
      eviction,
      dryRun = dryRun,
      fieldManager = fieldManager,
      fieldValidation = fieldValidation
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

  def attach(
      name: String,
      container: Option[String] = None,
      tty: Boolean = false,
      stdinEnabled: Boolean = false,
      stdoutEnabled: Boolean = true,
      stderrEnabled: Boolean = true
  ): PodAPI.Attach = PodAPI.Attach(
    namespace,
    name,
    container = container,
    tty = tty,
    stdinEnabled = stdinEnabled,
    stdoutEnabled = stdoutEnabled,
    stderrEnabled = stderrEnabled
  )
}

object ClusterPodAPI extends PodAPI.ClusterwideAPIBuilders
