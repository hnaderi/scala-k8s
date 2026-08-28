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

package dev.hnaderi.k8s.generator

import java.io.File

final class SourceCodeGenerator(
    managed: File,
    unmanaged: File
) {
  private var _createdFiles: List[File] = Nil
  def createdFiles: Seq[File] = _createdFiles

  private def fileName(base: File, pkg: String, name: String): File = {
    val pkgPath = pkg.replace('.', File.separatorChar)
    val dir = base.toPath().resolve(pkgPath)
    dir.toFile().mkdirs()
    dir.resolve(s"$name.scala").toFile()
  }

  def managed(pkg: String, name: String): SourceCode = {
    val file = fileName(managed, pkg, name)
    _createdFiles = _createdFiles :+ file
    new ManagedSourceCode(file)
  }
  def unmanaged(pkg: String, name: String): SourceCode =
    new UnmanagedSourceCode(fileName(unmanaged, pkg, name))
}

trait SourceCode {
  def write(code: String): Unit
}

/** Managed sources are owned by the generator, so stale output is always
  * replaced.
  */
final class ManagedSourceCode(file: File) extends SourceCode {
  def write(code: String): Unit = Utils.writeOutput(file, code)
}

/** Unmanaged sources are only seeded; once one exists it belongs to the
  * developer and is never overwritten.
  */
final class UnmanagedSourceCode(file: File) extends SourceCode {
  def write(code: String): Unit =
    if (!file.exists()) Utils.writeOutput(file, code)
}
