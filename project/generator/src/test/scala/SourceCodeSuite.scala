package dev.hnaderi.k8s.generator

import munit.FunSuite

import java.io.File
import java.nio.file.Files

class SourceCodeSuite extends FunSuite {

  private def withScg[A](f: (SourceCodeGenerator, File, File) => A): A = {
    val managed = Files.createTempDirectory("scg-managed").toFile
    val unmanaged = Files.createTempDirectory("scg-unmanaged").toFile
    managed.deleteOnExit()
    unmanaged.deleteOnExit()
    f(new SourceCodeGenerator(managed, unmanaged), managed, unmanaged)
  }

  private def contentOf(base: File, name: String) =
    Utils.loadFile(base.toPath.resolve(s"$name.scala").toFile)

  test("managed source is rewritten when the generated code changes") {
    withScg { (scg, managed, _) =>
      scg.managed("", "Example").write("first")
      scg.managed("", "Example").write("second")

      assert(contentOf(managed, "Example").startsWith("second"))
    }
  }

  test("unmanaged source is kept once it exists") {
    withScg { (scg, _, unmanaged) =>
      scg.unmanaged("", "Example").write("first")
      scg.unmanaged("", "Example").write("second")

      assert(contentOf(unmanaged, "Example").startsWith("first"))
    }
  }

  test("rewriting identical code leaves the file untouched") {
    withScg { (scg, managed, _) =>
      scg.managed("", "Example").write("same")
      val file = managed.toPath.resolve("Example.scala").toFile
      file.setLastModified(0L)

      scg.managed("", "Example").write("same")

      assertEquals(file.lastModified(), 0L)
    }
  }
}
