ThisBuild / tlBaseVersion := "0.1"

ThisBuild / organization := "dev.hnaderi"
ThisBuild / organizationName := "Hossein Naderi"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("hnaderi", "Hossein Naderi")
)

val scala212 = "2.12.16"
val scala213 = "2.13.8"
val scala3 = "3.1.3"
val PrimaryJava = JavaSpec.temurin("8")
val LTSJava = JavaSpec.temurin("17")

val supportScalaVersions = Seq(scala212, scala213, scala3)

ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := supportScalaVersions
ThisBuild / githubWorkflowJavaVersions := Seq(PrimaryJava, LTSJava)
ThisBuild / kubernetesVersion := "1.25.0"

enablePlugins(AutomateHeaderPlugin)

lazy val root =
  tlCrossRootProject
    .aggregate(objects, circe, manifests, docs, unidocs)
    .settings(
      name := "scala-k8s"
    )

lazy val circeVersion = "0.14.1"

val rootDir = Def.setting((ThisBuild / baseDirectory).value)

lazy val objects = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .settings(
    name := "scala-k8s-objects",
    description := "data models for kubernetes",
    k8sUnmanagedTarget := rootDir.value / "objects" / "src" / "main" / "scala"
  )
  .enablePlugins(KubernetesObjectGeneratorPlugin)

lazy val circe = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(
    name := "scala-k8s-circe",
    description := "circe codecs for kubernetes data models",
    k8sUnmanagedTarget := rootDir.value / "circe" / "src" / "main" / "scala",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % circeVersion
    )
  )
  .enablePlugins(KubernetesCirceCodecGenerator)
  .dependsOn(objects)

lazy val manifests = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("lib"))
  .settings(
    name := "scala-k8s-manifests",
    description := "kubernetes manifests utilities",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-yaml" % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion
    )
  )
  .dependsOn(circe)

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(manifests.jvm)
  .settings(
    tlSiteRelatedProjects := Seq(
      "Kubernetes" -> url("https://github.com/kubernetes/kubernetes"),
      "Circe" -> url("https://github.com/circe/circe")
    )
  )

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    name := "scala-k8s-docs",
    description := "unified docs for scala-k8s",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
      objects.jvm,
      circe.jvm,
      manifests.jvm
    )
  )
