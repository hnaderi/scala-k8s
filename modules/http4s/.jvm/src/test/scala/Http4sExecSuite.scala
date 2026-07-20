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

package dev.hnaderi.k8s.client.http4s

import cats.effect.IO
import dev.hnaderi.k8s.client._
import munit.CatsEffectSuite

import java.nio.file.Files
import java.nio.file.Path

class Http4sExecSuite extends CatsEffectSuite {

  private val cluster = Cluster(server = "https://example.test:6443")
  private val apiVersion = "client.authentication.k8s.io/v1beta1"

  private def writeScript(body: String): IO[Path] = IO.blocking {
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

  private def resolveParams(auth: AuthInfo): IO[AuthenticationParams] =
    Http4sExec.resolve[IO](auth, cluster).use(_._2)

  private def tokenScript(token: String, expiry: Option[String]): String = {
    val exp = expiry.fold("")(e => s""","expirationTimestamp":"$e"""")
    s"""echo '{"apiVersion":"$apiVersion","kind":"ExecCredential",""" +
      s""""status":{"token":"$token"$exp}}'
         |""".stripMargin
  }

  test("token plugin yields a bearer authenticator") {
    for {
      script <- writeScript(tokenScript("t0k3n", None))
      auth = AuthInfo(exec = Some(execConfig(script.toString)))
      params <- resolveParams(auth)
    } yield assertEquals(
      params.headers.toList,
      List("Authorization" -> "Bearer t0k3n")
    )
  }

  test("plugin receives KUBERNETES_EXEC_INFO and extra env") {
    for {
      out <- IO.blocking(Files.createTempFile("k8s-execinfo-", ".txt"))
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
      _ <- resolveParams(auth)
      content <- IO.blocking(new String(Files.readAllBytes(out), "UTF-8"))
    } yield {
      assert(content.contains(""""kind":"ExecCredential""""), content)
      assert(
        content.contains(""""server":"https://example.test:6443""""),
        content
      )
      assert(content.contains("hello"), content)
    }
  }

  test("non-zero exit surfaces stderr and installHint") {
    for {
      script <- writeScript("echo 'boom' 1>&2\nexit 3\n")
      auth = AuthInfo(
        exec = Some(
          execConfig(script.toString).copy(installHint = Some("please install"))
        )
      )
      result <- resolveParams(auth).attempt
    } yield {
      val msg = result.swap.toOption.map(_.getMessage).getOrElse("")
      assert(result.isLeft)
      assert(msg.contains("boom"), msg)
      assert(msg.contains("please install"), msg)
      assert(msg.contains("exit code 3"), msg)
    }
  }

  test("expired token triggers re-exec on each request") {
    for {
      counter <- IO.blocking(Files.createTempFile("k8s-count-", ".txt"))
      script <- writeScript(
        s"""printf x >> "${counter.toString}"
           |""".stripMargin +
          tokenScript("t", Some("2000-01-01T00:00:00Z"))
      )
      auth = AuthInfo(exec = Some(execConfig(script.toString)))
      _ <- Http4sExec.resolve[IO](auth, cluster).use { case (_, getP) =>
        getP >> getP
      }
      n <- IO.blocking(Files.readAllBytes(counter).length)
    } yield assertEquals(n, 3) // 1 initial run + 2 refreshes
  }

  test("unexpired token is cached across requests") {
    for {
      counter <- IO.blocking(Files.createTempFile("k8s-count-", ".txt"))
      script <- writeScript(
        s"""printf x >> "${counter.toString}"
           |""".stripMargin +
          tokenScript("t", Some("2999-01-01T00:00:00Z"))
      )
      auth = AuthInfo(exec = Some(execConfig(script.toString)))
      _ <- Http4sExec.resolve[IO](auth, cluster).use { case (_, getP) =>
        getP >> getP
      }
      n <- IO.blocking(Files.readAllBytes(counter).length)
    } yield assertEquals(n, 1) // fetched once, then cached
  }

  test("client-certificate plugin merges into AuthInfo for SSL") {
    for {
      script <- writeScript(
        s"""echo '{"apiVersion":"$apiVersion","kind":"ExecCredential",""" +
          s""""status":{"clientCertificateData":"CERT","clientKeyData":"KEY"}}'
             |""".stripMargin
      )
      auth = AuthInfo(exec = Some(execConfig(script.toString)))
      merged <- Http4sExec.resolve[IO](auth, cluster).use { case (a, _) =>
        IO.pure(a)
      }
    } yield {
      def decode(o: Option[String]): Option[String] =
        o.map(d => new String(java.util.Base64.getDecoder.decode(d), "UTF-8"))
      assertEquals(decode(merged.`client-certificate-data`), Some("CERT"))
      assertEquals(decode(merged.`client-key-data`), Some("KEY"))
    }
  }
}
