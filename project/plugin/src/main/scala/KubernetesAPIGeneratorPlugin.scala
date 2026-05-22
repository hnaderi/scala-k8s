package dev.hnaderi.k8s.generator

import sbt.{Keys => _, _}
import sbt.Keys._
import sbt.plugins.JvmPlugin

object KubernetesAPIGeneratorPlugin extends AutoPlugin {

  object autoImport {
    val k8sAPISkipKinds: SettingKey[Set[String]] = settingKey(
      "Fully-qualified kind names whose API files should not be generated (hand-maintained)"
    )
    val k8sAPITraitSkipKinds: SettingKey[Set[String]] = settingKey(
      "Fully-qualified kind names to exclude from generated group trait accessors"
    )
    val k8sAPISkipGroups: SettingKey[Set[(String, String)]] = settingKey(
      "API groups (group, version) whose trait files should not be generated"
    )
  }

  import autoImport._
  import KubernetesSpecPlugin.autoImport._
  import Keys._

  private val generate = Def.task {
    val log = streams.value.log
    val specFile = kubernetesSpecFile.value
    val defs = (Compile / kubernetesSpecFetch).value

    log.info("Parsing Kubernetes API paths ...")
    val resources = PathParser.loadResources(specFile, defs) match {
      case Left(err) =>
        throw new RuntimeException(
          s"Failed to parse Kubernetes API paths: $err"
        )
      case Right(rs) => rs
    }

    log.info(s"Generating API sources for ${resources.length} resources ...")

    val scg = new SourceCodeGenerator(
      managed = k8sManagedTarget.value,
      unmanaged = k8sUnmanagedTarget.value
    )

    val models = APIModel.build(
      resources,
      skipKinds = k8sAPISkipKinds.value,
      traitSkipKinds = k8sAPITraitSkipKinds.value,
      skipGroups = k8sAPISkipGroups.value
    )

    APIGenerator.write(scg, models)

    scg.createdFiles
  }

  override def trigger = noTrigger
  override def requires = JvmPlugin && KubernetesSpecPlugin

  override val projectSettings = Seq(
    k8sManagedTarget := (Compile / sourceManaged).value,
    k8sUnmanagedTarget := (Compile / scalaSource).value,
    k8sAPISkipKinds := Set.empty,
    k8sAPITraitSkipKinds := Set.empty,
    k8sAPISkipGroups := Set.empty,
    Compile / sourceGenerators += generate.taskValue,
    Compile / packageSrc / mappings ++= {
      val base = (Compile / sourceManaged).value
      val files = (Compile / managedSources).value
      files.map(f => (f, f.relativeTo(base).get.getPath))
    }
  )
}
