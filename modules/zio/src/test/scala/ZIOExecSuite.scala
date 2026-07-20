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

package dev.hnaderi.k8s.client.zio

import dev.hnaderi.k8s.client._
import _root_.zio._
import _root_.zio.test._

import java.nio.file.Files
import java.nio.file.Path

/** The zio-test default environment provides a `TestClock` fixed at the epoch,
  * so an expiry of `1970-...` reads as already expired and `2999-...` as fresh
  * without any explicit clock control.
  */
object ZIOExecSuite extends ZIOSpecDefault {

  private val cluster = Cluster(server = "https://example.test:6443")
  private val apiVersion = "client.authentication.k8s.io/v1beta1"

  private def writeScript(body: String): Task[Path] = ZIO.attemptBlocking {
    val p = Files.createTempFile("k8s-exec-", ".sh")
    Files.write(p, ("#!/bin/sh\n" + body).getBytes("UTF-8"))
    p.toFile.setExecutable(true)
    p
  }

  private def execConfig(
      command: String,
      env: Seq[ExecEnvVar] = Nil,
      provideClusterInfo: Boolean = false
  ): ExecConfig = ExecConfig(
    apiVersion = apiVersion,
    command = command,
    env = if (env.isEmpty) None else Some(env),
    provideClusterInfo = if (provideClusterInfo) Some(true) else None
  )

  private def tokenScript(token: String, expiry: Option[String]): String = {
    val exp = expiry.fold("")(e => s""","expirationTimestamp":"$e"""")
    s"""echo '{"apiVersion":"$apiVersion","kind":"ExecCredential",""" +
      s""""status":{"token":"$token"$exp}}'
         |""".stripMargin
  }

  def spec = suite("ZIOExec")(
    test("token plugin yields a bearer authenticator") {
      for {
        script <- writeScript(tokenScript("t0k3n", None))
        auth = AuthInfo(exec = Some(execConfig(script.toString)))
        params <- ZIOExec.resolve(auth, cluster).flatMap(_._2)
      } yield assertTrue(
        params.headers.toList == List("Authorization" -> "Bearer t0k3n")
      )
    },
    test("plugin receives KUBERNETES_EXEC_INFO and extra env") {
      for {
        out <- ZIO.attemptBlocking(
          Files.createTempFile("k8s-execinfo-", ".txt")
        )
        script <- writeScript(
          s"""printf '%s' "$$KUBERNETES_EXEC_INFO" > "${out.toString}"
             |echo "$$MY_VAR" >> "${out.toString}"
             |""".stripMargin + tokenScript("x", None)
        )
        auth = AuthInfo(
          exec = Some(
            execConfig(
              script.toString,
              env = Seq(ExecEnvVar("MY_VAR", "hello")),
              provideClusterInfo = true
            )
          )
        )
        _ <- ZIOExec.resolve(auth, cluster).flatMap(_._2)
        content <- ZIO.attemptBlocking(
          new String(Files.readAllBytes(out), "UTF-8")
        )
      } yield assertTrue(
        content.contains(""""kind":"ExecCredential""""),
        content.contains(""""server":"https://example.test:6443""""),
        content.contains("hello")
      )
    },
    test("non-zero exit surfaces stderr and installHint") {
      for {
        script <- writeScript("echo 'boom' 1>&2\nexit 3\n")
        auth = AuthInfo(
          exec = Some(
            execConfig(script.toString)
              .copy(installHint = Some("please install"))
          )
        )
        exit <- ZIOExec.resolve(auth, cluster).flatMap(_._2).exit
        msg = exit.causeOption
          .flatMap(_.failureOption)
          .map(_.getMessage)
          .getOrElse("")
      } yield assertTrue(
        exit.isFailure,
        msg.contains("boom"),
        msg.contains("please install"),
        msg.contains("exit code 3")
      )
    },
    test("expired token triggers re-exec on each request") {
      for {
        counter <- ZIO.attemptBlocking(
          Files.createTempFile("k8s-count-", ".txt")
        )
        script <- writeScript(
          s"""printf x >> "${counter.toString}"
             |""".stripMargin + tokenScript("t", Some("1970-01-01T00:00:00Z"))
        )
        auth = AuthInfo(exec = Some(execConfig(script.toString)))
        _ <- ZIOExec.resolve(auth, cluster).flatMap { case (_, getP) =>
          getP *> getP
        }
        n <- ZIO.attemptBlocking(Files.readAllBytes(counter).length)
      } yield assertTrue(n == 3) // 1 initial run + 2 refreshes
    },
    test("unexpired token is cached across requests") {
      for {
        counter <- ZIO.attemptBlocking(
          Files.createTempFile("k8s-count-", ".txt")
        )
        script <- writeScript(
          s"""printf x >> "${counter.toString}"
             |""".stripMargin + tokenScript("t", Some("2999-01-01T00:00:00Z"))
        )
        auth = AuthInfo(exec = Some(execConfig(script.toString)))
        _ <- ZIOExec.resolve(auth, cluster).flatMap { case (_, getP) =>
          getP *> getP
        }
        n <- ZIO.attemptBlocking(Files.readAllBytes(counter).length)
      } yield assertTrue(n == 1) // fetched once, then cached
    },
    test("client-certificate plugin merges into AuthInfo for SSL") {
      for {
        script <- writeScript(
          s"""echo '{"apiVersion":"$apiVersion","kind":"ExecCredential",""" +
            s""""status":{"clientCertificateData":"CERT","clientKeyData":"KEY"}}'
               |""".stripMargin
        )
        auth = AuthInfo(exec = Some(execConfig(script.toString)))
        merged <- ZIOExec.resolve(auth, cluster).map(_._1)
        decode = (o: Option[String]) =>
          o.map(d => new String(java.util.Base64.getDecoder.decode(d), "UTF-8"))
      } yield assertTrue(
        decode(merged.`client-certificate-data`).contains("CERT"),
        decode(merged.`client-key-data`).contains("KEY")
      )
    }
  )
}
