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

package dev.hnaderi.sbtk8s

import sbt.Keys._
import sbt._

import java.io.PrintWriter

object K8SPlugin extends AutoPlugin {
  object autoImport {
    val manifestObjects: SettingKey[Seq[K8SObject]] = SettingKey(
      "k8s objects to create"
    )
    val manifestFileName: SettingKey[String] = SettingKey("manifest file name")

    val manifestPrint = taskKey[Unit]("prints kubernetes manifests")
    val manifestGen = taskKey[Unit]("generate kubernetes manifest")
  }

  import autoImport._

  override def trigger = noTrigger
  override def requires = sbt.plugins.JvmPlugin

  override val projectSettings = Seq(
    manifestObjects := Nil,
    manifestGen / target := (ThisProject / target).value / "k8s",
    manifestFileName := "manifest.yml",
    manifestPrint := {
      println(s"staging files for ${name.value}")
      printManifest(manifestObjects.value)
    },
    manifestGen := {
      generateManifest(
        manifestObjects.value,
        (manifestGen / target).value,
        manifestFileName.value
      )
    }
  )

  private def generateManifest(
      objs: Seq[K8SObject],
      target: File,
      fileName: String
  ) = writeOutput(target, fileName)(manifestFor(objs))

  private def manifestFor(objs: Seq[K8SObject]): String = {
    val jsons = objs.map(_.buildManifest)
    Utils.toManifest(jsons: _*)
  }

  private def printManifest(objs: Seq[K8SObject]) = println(manifestFor(objs))

  private def writeOutput(buildTarget: File, outName: String)(
      content: String
  ) = {
    buildTarget.mkdirs()
    val file = new File(buildTarget, outName)
    val printWriter = new PrintWriter(file)

    try {
      printWriter.println(content)
    } finally {
      printWriter.close()
    }
  }
}
