package dev.hnaderi.k8s.generator

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

object KubernetesCirceCodecGenerator extends AutoPlugin {
  import Keys._
  object autoImport

  import autoImport._
  import KubernetesSpecPlugin.autoImport.kubernetesSpecFetch

  private val generate = Def.task {
    val log = streams.value.log
    val spec = (Compile / kubernetesSpecFetch).value
    val managed = k8sManagedTarget.value
    val unmanaged = k8sUnmanagedTarget.value

    log.info("Generating circe codec sources ...")
    val scg =
      new SourceCodeGenerator(managed = managed, unmanaged = unmanaged)
    val sources = spec.map { case (n, d) => DataModel(n, d) }
    CirceCodecGenerator.write(scg)(sources)

    scg.createdFiles
  }

  override def trigger = noTrigger
  override def requires = JvmPlugin && KubernetesSpecPlugin
  override val projectSettings = Seq(
    k8sManagedTarget := (Compile / sourceManaged).value,
    k8sUnmanagedTarget := (Compile / scalaSource).value,
    Compile / sourceGenerators += generate.taskValue,
    Compile / packageSrc / mappings ++= {
      val base = (Compile / sourceManaged).value
      val files = (Compile / managedSources).value
      files.map(f => (f, f.relativeTo(base).get.getPath))
    }
  )
}
