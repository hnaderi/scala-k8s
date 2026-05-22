package dev.hnaderi.k8s.generator

object Fixtures {

  def resource(
      kind: String,
      resourceName: String,
      group: String = "",
      version: String = "v1",
      isNamespaced: Boolean = true,
      subResources: Seq[SubResourceInfo] = Nil
  ): ResourceInfo = {
    val groupPkg =
      if (group.isEmpty) "core"
      else group.replace('.', '_').replace('-', '_')
    ResourceInfo(
      group = group,
      version = version,
      kind = kind,
      listKind = kind + "List",
      fqKind = s"io.k8s.$groupPkg.$version.$kind",
      fqListKind = s"io.k8s.$groupPkg.$version.${kind}List",
      resourceName = resourceName,
      isNamespaced = isNamespaced,
      subResources = subResources
    )
  }

  val scaleSubResource: SubResourceInfo =
    SubResourceInfo("scale", Set("get", "put"))

  val connectScaleSubResource: SubResourceInfo =
    SubResourceInfo("scale", Set("connect"))
}
