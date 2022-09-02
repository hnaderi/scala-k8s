import sbt._
import sbt.Keys._

object KubernetesObjectGeneratorPlugin extends AutoPlugin {
  object autoImport {
    val kubernetesObjectGenerate: TaskKey[Seq[File]] = taskKey(
      "Generates all objects from spec"
    )
  }

  import autoImport._
  import KubernetesSpecPlugin.autoImport.kubernetesSpecFetch

  override def trigger = noTrigger
  override def requires = KubernetesSpecPlugin
  override val projectSettings = Seq(
    Compile / kubernetesObjectGenerate := {
      val log = streams.value.log
      val spec = (Compile / kubernetesSpecFetch).value

      log.info("Generating sources ...")
      Seq()
    },
    Compile / sourceGenerators += (Compile / kubernetesObjectGenerate)
  )
}
