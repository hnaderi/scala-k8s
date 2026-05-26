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

import munit.FunSuite

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ListBuffer

class LineSplitterSuite extends FunSuite {

  private def buf(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8))

  private def feedAll(splitter: LineSplitter, chunks: String*): List[String] = {
    val out = ListBuffer.empty[String]
    chunks.foreach(c => splitter.feed(buf(c), out += _))
    out.toList
  }

  test("emits each complete line and drops empties") {
    assertEquals(
      feedAll(new LineSplitter, "alpha\nbeta\n"),
      List("alpha", "beta")
    )
  }

  test("buffers a partial trailing line until next chunk") {
    val s = new LineSplitter
    assertEquals(feedAll(s, "first\nseco"), List("first"))
    assertEquals(feedAll(s, "nd\n"), List("second"))
  }

  test("a single line spread across many chunks emerges once") {
    val s = new LineSplitter
    assertEquals(feedAll(s, "a", "b", "c", "\n"), List("abc"))
  }

  test("skips empty lines (consecutive newlines)") {
    assertEquals(
      feedAll(new LineSplitter, "a\n\nb\n"),
      List("a", "b")
    )
  }

  test("handles trailing data with no newline (held in buffer)") {
    val s = new LineSplitter
    assertEquals(feedAll(s, "no newline"), Nil)
  }

  test("decodes multi-byte UTF-8 across chunk boundary") {
    val bytes = "héllo\n".getBytes(StandardCharsets.UTF_8)
    val mid = bytes.length / 2 // likely splits the é (2 bytes)
    val s = new LineSplitter
    val out = ListBuffer.empty[String]
    s.feed(ByteBuffer.wrap(bytes.take(mid)), out += _)
    s.feed(ByteBuffer.wrap(bytes.drop(mid)), out += _)
    assertEquals(out.toList, List("héllo"))
  }
}
