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

import java.io.File

object SourceCode {
  def apply(t: (String, Definition)): SourceCode = SourceCode(t._1, t._2)
  def apply(name: String, definition: Definition): SourceCode = {
    val splitIdx = name.lastIndexOf(".")
    val pkgName = name.take(splitIdx)
    val fileName = name.drop(splitIdx + 1)
    new SourceCode(pkg = pkgName, name = fileName, definition)
  }
}

final class SourceCode(
    val pkg: String,
    val name: String,
    definition: Definition
) {
  private val model = DataModel(name, pkg, definition)

  def print: String = model.print
  def write(base: File): Unit = {
    val pkgPath = pkg.replace('.', File.separatorChar)
    val dir = base.toPath().resolve(pkgPath)
    dir.toFile().mkdirs()
    val file = dir.resolve(s"$name.scala").toFile()

    model.write(file)
  }
}
