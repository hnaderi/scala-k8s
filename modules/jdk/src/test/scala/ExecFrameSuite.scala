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
package jdk

import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.jawn.jawnFacade
import io.circe.Json
import munit.FunSuite

import java.nio.charset.StandardCharsets

class ExecFrameSuite extends FunSuite {

  private implicit val facade: org.typelevel.jawn.Facade.SimpleFacade[Json] =
    jawnFacade[Json]

  test("encode Stdin prefixes channel byte 0x00") {
    val data = "hi".getBytes(StandardCharsets.UTF_8)
    val bb = ExecFrame.encode(ExecInput.Stdin(data))
    val out = new Array[Byte](bb.remaining())
    bb.get(out)
    assertEquals(out.head, 0x00.toByte)
    assertEquals(new String(out.tail, StandardCharsets.UTF_8), "hi")
  }

  test("encode Resize prefixes channel byte 0x04 with JSON payload") {
    val bb = ExecFrame.encode(ExecInput.Resize(80, 24))
    val out = new Array[Byte](bb.remaining())
    bb.get(out)
    assertEquals(out.head, 0x04.toByte)
    assertEquals(
      new String(out.tail, StandardCharsets.UTF_8),
      """{"Width":80,"Height":24}"""
    )
  }

  test("decode channel 1 yields Stdout with payload") {
    val frame = Array[Byte](0x01) ++ "out".getBytes(StandardCharsets.UTF_8)
    val result = ExecFrame.decode[Json](frame)
    val Some(ExecEvent.Stdout(bytes)) = result: @unchecked
    assertEquals(new String(bytes, StandardCharsets.UTF_8), "out")
  }

  test("decode channel 2 yields Stderr with payload") {
    val frame = Array[Byte](0x02) ++ "err".getBytes(StandardCharsets.UTF_8)
    val result = ExecFrame.decode[Json](frame)
    val Some(ExecEvent.Stderr(bytes)) = result: @unchecked
    assertEquals(new String(bytes, StandardCharsets.UTF_8), "err")
  }

  test("decode channel 3 with valid Status JSON yields Error") {
    val body =
      """{"kind":"Status","apiVersion":"v1","status":"Failure","reason":"BadThing"}"""
        .getBytes(StandardCharsets.UTF_8)
    val frame = Array[Byte](0x03) ++ body
    val result = ExecFrame.decode[Json](frame)
    val Some(ExecEvent.Error(status)) = result: @unchecked
    assertEquals(status.reason, Some("BadThing"))
  }

  test("decode channel 3 with invalid JSON yields None") {
    val frame = Array[Byte](0x03) ++ "not json".getBytes(StandardCharsets.UTF_8)
    assertEquals(ExecFrame.decode[Json](frame), None)
  }

  test("decode empty frame yields None") {
    assertEquals(ExecFrame.decode[Json](Array.emptyByteArray), None)
  }

  test("decode unknown channel yields None") {
    val frame = Array[Byte](0x05) ++ "x".getBytes(StandardCharsets.UTF_8)
    assertEquals(ExecFrame.decode[Json](frame), None)
  }
}
