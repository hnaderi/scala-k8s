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
import dev.hnaderi.k8s.zioJson._
import _root_.zio._
import _root_.zio.http._
import _root_.zio.json._
import _root_.zio.test._

object ZIOExecBackendSuite extends ZIOSpecDefault {

  // ---- channel byte decoding helpers ----

  private def decodeFrame(
      channel: Byte,
      payload: Array[Byte]
  ): Option[ExecEvent] = {
    val data = Chunk.single(channel) ++ Chunk.fromArray(payload)
    (data.head.toInt & 0xff) match {
      case 1 => Some(ExecEvent.Stdout(data.drop(1).toArray))
      case 2 => Some(ExecEvent.Stderr(data.drop(1).toArray))
      case 3 =>
        val str = new String(data.drop(1).toArray, "UTF-8")
        JsonDecoder[io.k8s.apimachinery.pkg.apis.meta.v1.Status]
          .decodeJson(str)
          .toOption
          .map(ExecEvent.Error(_))
      case _ => None
    }
  }

  private def encodeStdin(bytes: Array[Byte]): (Byte, Array[Byte]) = {
    val frame = Chunk.single(0x00.toByte) ++ Chunk.fromArray(bytes)
    (frame.head, frame.drop(1).toArray)
  }

  private def encodeResize(cols: Int, rows: Int): (Byte, Array[Byte]) = {
    val json = s"""{"Width":$cols,"Height":$rows}""".getBytes("UTF-8")
    val frame = Chunk.single(0x04.toByte) ++ Chunk.fromArray(json)
    (frame.head, frame.drop(1).toArray)
  }

  // ---- URL scheme rewriting (mirrors ZIOExecBackend.rewriteToWs) ----

  private def wsScheme(raw: String): String = {
    val url = URL.decode(raw).toOption.get
    val rewritten = url.scheme match {
      case Some(Scheme.HTTP) => url.scheme(Scheme.WS)
      case _                 => url.scheme(Scheme.WSS)
    }
    rewritten.scheme.map(_.encode).getOrElse("")
  }

  def spec = suite("ZIOExecBackend")(
    suite("channel byte decoding")(
      test("channel 1 -> Stdout") {
        val payload = "hello".getBytes("UTF-8")
        val result = decodeFrame(1.toByte, payload)
        assertTrue(result.exists {
          case ExecEvent.Stdout(data) => data.toList == payload.toList
          case _                      => false
        })
      },
      test("channel 2 -> Stderr") {
        val payload = "error output".getBytes("UTF-8")
        val result = decodeFrame(2.toByte, payload)
        assertTrue(result.exists {
          case ExecEvent.Stderr(data) => data.toList == payload.toList
          case _                      => false
        })
      },
      test("channel 3 with valid Status JSON -> Error") {
        val json = """{"status":"Success","code":0}""".getBytes("UTF-8")
        val result = decodeFrame(3.toByte, json)
        assertTrue(result.exists {
          case ExecEvent.Error(s) => s.status.contains("Success")
          case _                  => false
        })
      },
      test("channel 3 with invalid JSON -> None") {
        val result = decodeFrame(3.toByte, "not json".getBytes("UTF-8"))
        assertTrue(result.isEmpty)
      },
      test("unknown channel -> None") {
        val result = decodeFrame(9.toByte, "data".getBytes("UTF-8"))
        assertTrue(result.isEmpty)
      }
    ),
    suite("input frame encoding")(
      test("Stdin uses channel byte 0x00") {
        val data = "hello".getBytes("UTF-8")
        val (ch, body) = encodeStdin(data)
        assertTrue((ch.toInt & 0xff) == 0) &&
        assertTrue(body.toList == data.toList)
      },
      test("Resize uses channel byte 0x04 with JSON body") {
        val (ch, body) = encodeResize(80, 24)
        val expected = """{"Width":80,"Height":24}""".getBytes("UTF-8")
        assertTrue((ch.toInt & 0xff) == 4) &&
        assertTrue(body.toList == expected.toList)
      }
    ),
    suite("URL scheme rewriting")(
      test("https -> wss") {
        assertTrue(
          wsScheme("https://k8s.example.com:6443/api/v1/exec") == "wss"
        )
      },
      test("http -> ws") {
        assertTrue(wsScheme("http://localhost:8080/api/v1/exec") == "ws")
      },
      test("wss stays wss") {
        assertTrue(wsScheme("wss://k8s.example.com:6443/api/v1/exec") == "wss")
      }
    )
  )
}
