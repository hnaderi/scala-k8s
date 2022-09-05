package dev.hnaderi.k8s.generator

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

object KubernetesObjectGeneratorPlugin extends AutoPlugin {
  object autoImport extends Keys

  import autoImport._
  import KubernetesSpecPlugin.autoImport.kubernetesSpecFetch

  private val generate = Def.task {
    val log = streams.value.log
    val spec = (Compile / kubernetesSpecFetch).value
    val managed = k8sManagedTarget.value
    val unmanaged = k8sUnmanagedTarget.value

    log.info("Generating sources ...")
    val scg =
      new SourceCodeGenerator(managed = managed, unmanaged = unmanaged)
    val sources = spec.map { case (n, d) => DataModel(n, d) }
    sources.foreach(ObjectGenerator.write(scg))

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
