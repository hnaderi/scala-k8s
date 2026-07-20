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
package http4s

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Mutex
import cats.syntax.all._
import dev.hnaderi.k8s.manifest

import fs2.io.process.ProcessBuilder
import fs2.io.process.Processes

import java.time.OffsetDateTime

/** Runs kubeconfig `exec` credential plugins and turns their output into a
  * per-request [[AuthenticationParams]] effect. Cross-platform via
  * `fs2.io.process` (JVM/Node/Native).
  */
private[http4s] object Http4sExec {

  /** Refresh a token this many milliseconds before its stated expiry. */
  private val ExpirySkewMillis = 30000L

  /** Resolves the authenticator for a user. When the user has no `exec` block
    * this is the static credential; otherwise the plugin is run once (to learn
    * the credential type and, for certificates, feed SSL), and token
    * credentials get a cached, auto-refreshing provider.
    *
    * @return
    *   the (possibly cert-augmented) [[AuthInfo]] to build SSL from, and an
    *   effect producing fresh [[AuthenticationParams]] per request
    */
  def resolve[F[_]](
      auth: AuthInfo,
      cluster: Cluster
  )(implicit F: Async[F]): Resource[F, (AuthInfo, F[AuthenticationParams])] =
    auth.exec match {
      case None =>
        Resource.pure((auth, F.pure(AuthenticationParams.from(auth))))
      case Some(exec) =>
        Resource.eval(run(exec, cluster)).flatMap { first =>
          if (first.token.exists(_.nonEmpty))
            Resource
              .eval(bearerProvider(exec, cluster, first))
              .map(getParams => (auth, getParams))
          else {
            val merged = ExecCredentials.merge(auth, first)
            Resource.pure((merged, F.pure(AuthenticationParams.from(merged))))
          }
        }
    }

  private def run[F[_]](exec: ExecConfig, cluster: Cluster)(implicit
      F: Async[F]
  ): F[ExecCredentialStatus] = {
    val extraEnv =
      exec.env.getOrElse(Nil).map(e => e.name -> e.value).toMap +
        ("KUBERNETES_EXEC_INFO" ->
          ExecCredentials.execInfo(exec, Some(cluster)).printJson)
    val pb = ProcessBuilder(exec.command, exec.args.getOrElse(Nil).toList)
      .withInheritEnv(true)
      .withExtraEnv(extraEnv)

    Processes.forAsync[F].spawn(pb).use { proc =>
      F.both(
        proc.stdout.through(fs2.text.utf8.decode).compile.string,
        proc.stderr.through(fs2.text.utf8.decode).compile.string
      ).flatMap { case (out, err) =>
        proc.exitValue.flatMap { code =>
          if (code != 0)
            F.raiseError[ExecCredentialStatus](
              new RuntimeException(execError(exec, code, err))
            )
          else
            F.fromEither(
              manifest
                .parse[ExecCredential](out)
                .leftMap(t => new RuntimeException(t.getMessage): Throwable)
                .flatMap(cred =>
                  ExecCredentials
                    .validate(exec, cred)
                    .leftMap(new RuntimeException(_): Throwable)
                )
            )
        }
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

  private def bearerProvider[F[_]](
      exec: ExecConfig,
      cluster: Cluster,
      first: ExecCredentialStatus
  )(implicit F: Async[F]): F[F[AuthenticationParams]] =
    for {
      ref <- F.ref(first)
      mutex <- Mutex[F]
    } yield {
      def expired(s: ExecCredentialStatus, nowMillis: Long): Boolean =
        s.expirationTimestamp
          .flatMap(expiryMillis)
          .exists(exp => nowMillis >= exp - ExpirySkewMillis)

      val refresh: F[ExecCredentialStatus] =
        mutex.lock.surround {
          for {
            latest <- ref.get
            now <- F.realTime
            result <-
              if (expired(latest, now.toMillis))
                run(exec, cluster).flatTap(ref.set)
              else F.pure(latest)
          } yield result
        }

      for {
        cached <- ref.get
        now <- F.realTime
        status <- if (expired(cached, now.toMillis)) refresh else F.pure(cached)
      } yield status.token.fold(AuthenticationParams.empty)(
        AuthenticationParams.bearer(_)
      )
    }
}
