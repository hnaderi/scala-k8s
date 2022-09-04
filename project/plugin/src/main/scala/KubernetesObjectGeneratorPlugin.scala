package dev.hnaderi.k8s.generator

import sbt._
import sbt.Keys._

object KubernetesObjectGeneratorPlugin extends AutoPlugin {
  object autoImport extends Keys {
    val k8sObjectGenerate: TaskKey[Seq[File]] = taskKey(
      "Generates all objects from spec"
    )
  }

  import autoImport._
  import KubernetesSpecPlugin.autoImport.kubernetesSpecFetch

  override def trigger = noTrigger
  override def requires = KubernetesSpecPlugin
  override val projectSettings = Seq(
    k8sManagedTarget := (Compile / sourceManaged).value,
    k8sUnmanagedTarget := (Compile / scalaSource).value,
    Compile / k8sObjectGenerate := {
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
    },
    Compile / sourceGenerators += (Compile / k8sObjectGenerate)
  )
}
