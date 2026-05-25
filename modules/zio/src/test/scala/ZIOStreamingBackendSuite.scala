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

import dev.hnaderi.k8s.jawn.jawnFacade
import dev.hnaderi.k8s.zioJson._
import org.typelevel.jawn.AsyncParser
import _root_.zio._
import _root_.zio.json.ast.Json
import _root_.zio.stream.ZStream
import _root_.zio.test._

object ZIOStreamingBackendSuite extends ZIOSpecDefault {

  def spec = suite("ZIOStreamingBackend - JSON streaming")(
    test("parses a single JSON value") {
      val json = """{"kind":"Pod"}""".getBytes("UTF-8")
      val parser = AsyncParser[Json](AsyncParser.ValueStream)
      val result = parser.absorb(json)
      assertTrue(result.isRight && result.toOption.exists(_.nonEmpty))
    },
    test("parses multiple NDJSON lines") {
      val bytes = List("""{"a":1}""", """{"b":2}""", """{"c":3}""")
        .mkString("\n")
        .getBytes("UTF-8")
      val parser = AsyncParser[Json](AsyncParser.ValueStream)
      val result = parser.absorb(bytes)
      assertTrue(result.isRight && result.toOption.map(_.size).contains(3))
    },
    test("ZStream.fromZIO allocates parser fresh on each run") {
      val parserStream =
        ZStream.fromZIO(ZIO.succeed(AsyncParser[Json](AsyncParser.ValueStream)))
      for {
        p1 <- parserStream.runCollect
        p2 <- parserStream.runCollect
      } yield assertTrue(p1.head ne p2.head)
    }
  )
}
