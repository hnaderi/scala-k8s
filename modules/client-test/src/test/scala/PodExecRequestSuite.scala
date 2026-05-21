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

import apis.corev1.PodAPI
import munit.FunSuite

class PodExecRequestSuite extends FunSuite {

  private val ns = "test-ns"
  private val podName = "test-pod"

  private def capture(req: ExecRequest): (String, Seq[(String, String)]) = {
    var url = ""
    var params: Seq[(String, String)] = Nil
    val client = new ExecClient[List] {
      def exec(
          u: String,
          p: (String, String)*
      ): List[ExecInput] => List[ExecEvent] = {
        url = u
        params = p
        _ => Nil
      }
    }
    req.exec(client)
    (url, params)
  }

  test("URL ends with namespaced pod exec path") {
    val (url, _) = capture(PodAPI.Exec(ns, podName, Seq("ls")))
    assertEquals(url, "/api/v1/namespaces/test-ns/pods/test-pod/exec")
  }

  test("command appears as repeated params") {
    val (_, params) =
      capture(PodAPI.Exec(ns, podName, Seq("sh", "-c", "echo hello")))
    assertEquals(
      params.filter(_._1 == "command").map(_._2).toList,
      List("sh", "-c", "echo hello")
    )
  }

  test("default flags: stdout/stderr on, stdin/tty off") {
    val (_, params) = capture(PodAPI.Exec(ns, podName, Seq("ls")))
    val m = params.toMap
    assertEquals(m("stdout"), "true")
    assertEquals(m("stderr"), "true")
    assertEquals(m("stdin"), "false")
    assertEquals(m("tty"), "false")
  }

  test("container param included when specified") {
    val (_, params) =
      capture(PodAPI.Exec(ns, podName, Seq("ls"), container = Some("sidecar")))
    assertEquals(
      params.filter(_._1 == "container").map(_._2).toList,
      List("sidecar")
    )
  }

  test("container param absent when not specified") {
    val (_, params) = capture(PodAPI.Exec(ns, podName, Seq("ls")))
    assert(!params.exists(_._1 == "container"))
  }

  test("tty and stdin flags can be enabled") {
    val (_, params) = capture(
      PodAPI.Exec(ns, podName, Seq("sh"), tty = true, stdinEnabled = true)
    )
    val m = params.toMap
    assertEquals(m("tty"), "true")
    assertEquals(m("stdin"), "true")
  }

  test("stdout and stderr can be disabled") {
    val (_, params) = capture(
      PodAPI.Exec(
        ns,
        podName,
        Seq("ls"),
        stdoutEnabled = false,
        stderrEnabled = false
      )
    )
    val m = params.toMap
    assertEquals(m("stdout"), "false")
    assertEquals(m("stderr"), "false")
  }

  test("PodAPI namespace builder produces the same request") {
    val fromObject = PodAPI.Exec(ns, podName, Seq("ls"), container = Some("c"))
    val fromBuilder = PodAPI(ns).exec(podName, Seq("ls"), container = Some("c"))
    assertEquals(capture(fromObject), capture(fromBuilder))
  }
}
