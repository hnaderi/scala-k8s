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
package zio

import dev.hnaderi.k8s.manifest
import _root_.zio._

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/** Runs kubeconfig `exec` credential plugins and turns their output into a
  * per-request [[AuthenticationParams]] effect. JVM-only (uses
  * `java.lang.ProcessBuilder`).
  */
private[zio] object ZIOExec {

  /** Refresh a token this many milliseconds before its stated expiry. */
  private val ExpirySkewMillis = 30000L

  def resolve(
      auth: AuthInfo,
      cluster: Cluster
  ): Task[(AuthInfo, Task[AuthenticationParams])] =
    auth.exec match {
      case None =>
        ZIO.succeed((auth, ZIO.succeed(AuthenticationParams.from(auth))))
      case Some(exec) =>
        run(exec, cluster).flatMap { first =>
          if (first.token.exists(_.nonEmpty))
            bearerProvider(exec, cluster, first)
              .map(getParams => (auth, getParams))
          else {
            val merged = ExecCredentials.merge(auth, first)
            ZIO.succeed(
              (merged, ZIO.succeed(AuthenticationParams.from(merged)))
            )
          }
        }
    }

  private def run(
      exec: ExecConfig,
      cluster: Cluster
  ): Task[ExecCredentialStatus] =
    ZIO
      .attemptBlocking {
        val json = ExecCredentials.execInfo(exec, Some(cluster)).printJson
        val cmd = new java.util.ArrayList[String]()
        cmd.add(exec.command)
        exec.args.getOrElse(Nil).foreach(cmd.add)
        val pb = new java.lang.ProcessBuilder(cmd)
        val env = pb.environment()
        exec.env.getOrElse(Nil).foreach(e => env.put(e.name, e.value))
        env.put("KUBERNETES_EXEC_INFO", json)
        pb.start()
      }
      .flatMap { proc =>
        val readOut =
          ZIO.attemptBlocking(
            new String(proc.getInputStream.readAllBytes(), "UTF-8")
          )
        val readErr =
          ZIO.attemptBlocking(
            new String(proc.getErrorStream.readAllBytes(), "UTF-8")
          )
        readOut.zipPar(readErr).flatMap { case (out, err) =>
          ZIO.attemptBlocking(proc.waitFor()).flatMap { code =>
            if (code != 0)
              ZIO.fail(new RuntimeException(execError(exec, code, err)))
            else
              ZIO.fromEither(
                manifest
                  .parse[ExecCredential](out)
                  .left
                  .map(t => new RuntimeException(t.getMessage): Throwable)
                  .flatMap(cred =>
                    ExecCredentials
                      .validate(exec, cred)
                      .left
                      .map(new RuntimeException(_): Throwable)
                  )
              )
          }
        }
      }

  private def execError(exec: ExecConfig, code: Int, stderr: String): String =
    s"exec credential plugin '${exec.command}' failed with exit code $code" +
      (if (stderr.trim.nonEmpty) s": ${stderr.trim}" else "") +
      exec.installHint.fold("")(h => s"\n$h")

  private def expiryMillis(rfc3339: String): Option[Long] =
    try Some(OffsetDateTime.parse(rfc3339).toInstant.toEpochMilli)
    catch { case _: Throwable => None }

  private def bearerProvider(
      exec: ExecConfig,
      cluster: Cluster,
      first: ExecCredentialStatus
  ): UIO[Task[AuthenticationParams]] =
    for {
      ref <- Ref.make(first)
      sem <- Semaphore.make(1)
    } yield {
      def expired(s: ExecCredentialStatus, nowMillis: Long): Boolean =
        s.expirationTimestamp
          .flatMap(expiryMillis)
          .exists(exp => nowMillis >= exp - ExpirySkewMillis)

      val refresh: Task[ExecCredentialStatus] =
        sem.withPermit {
          for {
            latest <- ref.get
            now <- Clock.currentTime(TimeUnit.MILLISECONDS)
            result <-
              if (expired(latest, now)) run(exec, cluster).tap(ref.set)
              else ZIO.succeed(latest)
          } yield result
        }

      for {
        cached <- ref.get
        now <- Clock.currentTime(TimeUnit.MILLISECONDS)
        status <- if (expired(cached, now)) refresh else ZIO.succeed(cached)
      } yield status.token.fold(AuthenticationParams.empty)(
        AuthenticationParams.bearer(_)
      )
    }
}
