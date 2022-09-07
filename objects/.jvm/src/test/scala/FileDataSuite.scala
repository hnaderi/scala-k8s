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

package dev.hnaderi.k8s

import munit.FunSuite

import java.io.File

class FileDataSuite extends FunSuite {
  test("file value") {
    val file = new File("some.file")
    assertEquals(Data.file("some.file"), Data(file))
  }

  test("directory") {
    val dir = DataMap.fromDir(new File("objects/src/test/resources/data"))
    assertEquals(
      dir,
      Map(
        "abc.txt" -> "abcdef\n",
        "app.conf" -> "key = value\n"
      )
    )
  }

  test("directory binary") {
    val dir = DataMap.binaryFromDir(new File("objects/src/test/resources/data"))
    assertEquals(
      dir,
      Map(
        "abc.txt" -> "YWJjZGVmCg==",
        "app.conf" -> "a2V5ID0gdmFsdWUK"
      )
    )
  }
}
