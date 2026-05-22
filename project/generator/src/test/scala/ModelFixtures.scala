package dev.hnaderi.k8s.generator

import java.nio.file.Files

object ModelFixtures {

  // ---- properties ----

  val nameStr = ModelProperty(
    "name",
    ModelPropertyType.Primitive("String"),
    required = true
  )
  val nsOpt = ModelProperty(
    "namespace",
    ModelPropertyType.Primitive("String"),
    required = false,
    default = Some("None")
  )
  val labels = ModelProperty(
    "labels",
    ModelPropertyType.Object("String"),
    required = false,
    default = Some("None")
  )
  val containerItems = ModelProperty(
    "items",
    ModelPropertyType.List("String"),
    required = true
  )
  val metadataRef = ModelProperty(
    "metadata",
    ModelPropertyType.Ref("io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta"),
    required = false,
    default = Some("None")
  )

  // ---- kinds ----

  val coreKind = Kind("", "ConfigMap", "v1")
  val appsKind = Kind("apps", "Deployment", "v1")

  // ---- data models ----

  val configMap = DataModel.Resource(
    name = "ConfigMap",
    pkg = "io.k8s.api.core.v1",
    description = Some("ConfigMap holds configuration data"),
    properties = Seq(nameStr, nsOpt, labels),
    kind = coreKind
  )

  val objectMeta = DataModel.SubResource(
    name = "ObjectMeta",
    pkg = "io.k8s.apimachinery.pkg.apis.meta.v1",
    description = None,
    properties = Seq(nameStr, nsOpt, labels)
  )

  val watchEvent = DataModel.MetaResource(
    name = "WatchEvent",
    pkg = "io.k8s.apimachinery.pkg.apis.meta.v1",
    description = None,
    properties = Seq(nameStr, nsOpt),
    kinds = Seq(coreKind, appsKind)
  )

  val intOrString = DataModel.Primitive(
    name = "IntOrString",
    pkg = "io.k8s.apimachinery.pkg.util.intstr",
    description = None
  )

  // ---- test infrastructure ----

  def withScg[A](
      f: SourceCodeGenerator => A
  ): (Map[String, String], Map[String, String]) = {
    val managed = Files.createTempDirectory("scg-managed").toFile
    val unmanaged = Files.createTempDirectory("scg-unmanaged").toFile
    managed.deleteOnExit()
    unmanaged.deleteOnExit()
    val scg = new SourceCodeGenerator(managed = managed, unmanaged = unmanaged)
    f(scg)
    (readDir(managed), readDir(unmanaged))
  }

  private def readDir(dir: java.io.File): Map[String, String] = {
    def walk(f: java.io.File): Seq[java.io.File] =
      if (f.isDirectory) f.listFiles().toSeq.flatMap(walk) else Seq(f)
    walk(dir)
      .map(f =>
        f.getName.stripSuffix(".scala") -> scala.io.Source.fromFile(f).mkString
      )
      .toMap
  }
}
