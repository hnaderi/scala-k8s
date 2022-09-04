package dev.hnaderi.k8s.generator

import sbt._
import sbt.Keys._

object KubernetesSpecPlugin extends AutoPlugin {
  object autoImport {
    val kubernetesVersion: SettingKey[String] = settingKey(
      "Version of kubernetes to support"
    )
    val kubernetesSpecFileName: SettingKey[String] = settingKey(
      "Name for local version of downloaded spec"
    )
    val kubernetesSpecFile: SettingKey[File] = settingKey(
      "Locally downloaded spec file"
    )
    val kubernetesSpecificationDir: SettingKey[File] = settingKey(
      "Directory where downloaded spec files are stored"
    )
    val kubernetesSpecAddress: SettingKey[URI] = settingKey(
      "Version of kubernetes to support"
    )
    val kubernetesSpecFetch: TaskKey[Map[String, Definition]] = taskKey(
      "Fetch specified spec version from kubernetes repository"
    )
  }

  import autoImport._
  override def trigger = noTrigger
  override def requires = sbt.plugins.JvmPlugin
  override val globalSettings = Seq(
    kubernetesSpecificationDir := (ThisBuild / baseDirectory).value / "specifications",
    kubernetesSpecAddress := uri(
      s"https://github.com/kubernetes/kubernetes/raw/v${(ThisBuild / kubernetesVersion).value}/api/openapi-spec/swagger.json"
    ),
    kubernetesSpecFileName := s"kubernetes-spec-v${(ThisBuild / kubernetesVersion).value}.json",
    kubernetesSpecFile := (ThisBuild / kubernetesSpecificationDir).value / (ThisBuild / kubernetesSpecFileName).value,
    Compile / kubernetesSpecFetch := {
      val uri = kubernetesSpecAddress.value
      val targetFile = kubernetesSpecFile.value

      targetFile.getParentFile().mkdirs()

      val log = streams.value.log

      if (targetFile.exists())
        log.info("Kubernetes spec exists, skipping download.")
      else {
        import scala.sys.process._
        log.info(s"Downloading kubernetes specs from $uri ...")
        uri.toURL #> targetFile !
      }

      Utils.loadDefinitions(targetFile) match {
        case Left(err) =>
          log.error(s"Invalid kubernetes API specification!")
          throw err
        case Right(defs) => defs
      }
    }
  )
}
