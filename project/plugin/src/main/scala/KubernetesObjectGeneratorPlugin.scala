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
      val managed = (Compile / sourceManaged).value
      val unmanaged = (Compile / sourceDirectory).value / "scala"

      log.info("Generating sources ...")
      val scg =
        new SourceCodeGenerator(managed = managed, unmanaged = unmanaged)
      Utils.loadDefinitions(spec) match {
        case Left(err) =>
          log.error(s"Invalid kubernetes API specification!")
          throw err
        case Right(defs) =>
          val sources = defs.map { case (n, d) => DataModel(n, d) }
          sources.foreach(_.write(scg))
      }

      scg.createdFiles
    },
    Compile / sourceGenerators += (Compile / kubernetesObjectGenerate)
  )
}
