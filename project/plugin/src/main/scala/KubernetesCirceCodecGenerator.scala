package dev.hnaderi.k8s.generator

import sbt._
import sbt.Keys._

object KubernetesCirceCodecGenerator extends AutoPlugin {
  import Keys._
  object autoImport {
    val k8sCodecGenerate: TaskKey[Seq[File]] = taskKey(
      "Generates all codecs from spec"
    )
  }

  import autoImport._
  import KubernetesSpecPlugin.autoImport.kubernetesSpecFetch

  override def trigger = noTrigger
  override def requires = KubernetesSpecPlugin
  override val projectSettings = Seq(
    k8sManagedTarget := (Compile / sourceManaged).value,
    k8sUnmanagedTarget := (Compile / scalaSource).value,
    Compile / k8sCodecGenerate := {
      val log = streams.value.log
      val spec = (Compile / kubernetesSpecFetch).value
      val managed = k8sManagedTarget.value
      val unmanaged = k8sUnmanagedTarget.value

      log.info("Generating circe codec sources ...")
      val scg =
        new SourceCodeGenerator(managed = managed, unmanaged = unmanaged)
      Utils.loadDefinitions(spec) match {
        case Left(err) =>
          log.error(s"Invalid kubernetes API specification!")
          throw err
        case Right(defs) =>
          val sources = defs.map { case (n, d) => DataModel(n, d) }
          CirceCodecGenerator.write(scg)(sources)
      }

      scg.createdFiles
    },
    Compile / sourceGenerators += (Compile / k8sCodecGenerate)
  )
}
