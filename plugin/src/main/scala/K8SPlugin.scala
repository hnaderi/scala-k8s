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

import _root_.io.circe.Json
import sbt.Keys._
import sbt._

import java.io.PrintWriter

object K8SPlugin extends AutoPlugin {
  object autoImport {
    val K8S: Configuration = config("k8s")

    val spec: SettingKey[Seq[K8SObject]] = SettingKey(
      "k8s objects to create"
    )
    val manifestName: SettingKey[String] = SettingKey("manifest file name")

    val print = taskKey[Unit]("prints kubernetes manifests")
    val gen = taskKey[Unit]("generate kubernetes manifest")
  }

  import autoImport._

  override def trigger = noTrigger
  override def requires = sbt.plugins.JvmPlugin

  override val projectSettings =
    sbt.inConfig(K8S)(deploymentSettings)

  private def generateManifest(
      objs: Seq[K8SObject],
      target: File,
      fileName: String
  ) = {
    val jsons = objs.map(_.buildManifest)
    writeManifests(target, fileName)(jsons: _*)
  }

  private def printManifest(objs: Seq[K8SObject]) = {
    val jsons = objs.map(_.buildManifest)
    val manifest = Utils.toManifest(jsons: _*)

    println(manifest)
  }

  private def writeManifests(buildTarget: File, outName: String)(
      manifests: Json*
  ) = {
    buildTarget.mkdirs()
    val file = new File(buildTarget, outName)
    val printWriter = new PrintWriter(file)

    val content = Utils.toManifest(manifests: _*)

    try {
      printWriter.println(content)
    } finally {
      printWriter.close()
    }
  }

  lazy val deploymentSettings: Seq[Def.Setting[_]] = Seq(
    K8S / spec := Nil,
    K8S / target := (ThisProject / target).value / "k8s",
    K8S / manifestName := "manifest.yml",
    K8S / print := {
      println(s"staging files for ${name.value}")
      printManifest((K8S / spec).value)
    },
    K8S / gen := {
      generateManifest(
        (K8S / spec).value,
        (K8S / target).value,
        (K8S / manifestName).value
      )
    }
  )
}
