ThisBuild / tlBaseVersion := "0.0"

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
val supportScalaVersions = Seq(scala212, scala213, scala3)

ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / scalaVersion := scala212

lazy val root =
  tlCrossRootProject
    .aggregate(lib, manifest, cookbook, core, docs)
    .enablePlugins(NoPublishPlugin)

lazy val lib = project
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "k8s-objects",
    libraryDependencies ++= Seq(
      "com.goyeau" %% "kubernetes-client" % "0.8.1"
    ),
    crossScalaVersions := supportScalaVersions
  )

lazy val manifest = project
  .enablePlugins(AutomateHeaderPlugin, SbtPlugin)
  .settings(
    name := "sbt-k8s-manifest",
    pluginCrossBuild / sbtVersion := "1.2.8" // set minimum sbt version
  )
  .dependsOn(lib)

lazy val cookbook = project
  .enablePlugins(AutomateHeaderPlugin, SbtPlugin)
  .settings(
    name := "sbt-k8s-cookbook",
    pluginCrossBuild / sbtVersion := "1.2.8" // set minimum sbt version
  )
  .dependsOn(manifest)

lazy val core = project
  .enablePlugins(AutomateHeaderPlugin, SbtPlugin)
  .settings(
    name := "sbt-k8s",
    pluginCrossBuild / sbtVersion := "1.2.8" // set minimum sbt version
  )
  .dependsOn(manifest, cookbook)

lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)
