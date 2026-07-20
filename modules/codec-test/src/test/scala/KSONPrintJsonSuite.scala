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

package dev.hnaderi.k8s.utils

import dev.hnaderi.k8s.utils.KSON._
import munit.FunSuite

class KSONPrintJsonSuite extends FunSuite {

  test("prints scalars") {
    assertEquals(KString("hi").printJson, "\"hi\"")
    assertEquals(KInt(42).printJson, "42")
    assertEquals(KLong(9000000000L).printJson, "9000000000")
    assertEquals(KBool(true).printJson, "true")
    assertEquals(KBool(false).printJson, "false")
    assertEquals(KNull.printJson, "null")
  }

  test("prints arrays and objects with insertion order and no spaces") {
    assertEquals(KArr(List(KInt(1), KInt(2), KInt(3))).printJson, "[1,2,3]")
    assertEquals(
      KObj(List("b" -> KInt(1), "a" -> KString("x"))).printJson,
      """{"b":1,"a":"x"}"""
    )
  }

  test("nests objects and arrays") {
    assertEquals(
      KObj(
        List("spec" -> KObj(List("items" -> KArr(List(KBool(false), KNull)))))
      ).printJson,
      """{"spec":{"items":[false,null]}}"""
    )
  }

  test("escapes JSON string metacharacters and control chars") {
    assertEquals(
      KString("a\"b\\c\nd\te").printJson,
      "\"a\\\"b\\\\c\\nd\\te\""
    )
    // a bell (U+0007) has no shorthand escape and becomes a unicode escape
    assertEquals(KString(7.toChar.toString).printJson, "\"\\u0007\"")
  }
}
