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

final class SourceCodeGenerator(
    managed: File,
    unmanaged: File
) {
  private def fileName(base: File, pkg: String, name: String): File = {
    val pkgPath = pkg.replace('.', File.separatorChar)
    val dir = base.toPath().resolve(pkgPath)
    dir.toFile().mkdirs()
    dir.resolve(s"$name.scala").toFile()
  }

  def managed(pkg: String, name: String): SourceCode =
    new ManagedSourceCode(fileName(managed, pkg, name))
  def unmanaged(pkg: String, name: String): SourceCode =
    new UnManagedSourceCode(fileName(unmanaged, pkg, name))
}

trait SourceCode {
  def write(code: String): Unit
}
final class ManagedSourceCode(file: File) extends SourceCode {
  def write(code: String): Unit = Utils.writeOutput(file, code)
}
final class UnManagedSourceCode(file: File) extends SourceCode {
  def write(code: String): Unit =
    if (!file.exists()) Utils.writeOutput(file, code)
}
