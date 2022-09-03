import org.typelevel.sbt.gha.WorkflowStep.Sbt

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
ThisBuild / tlSitePublishBranch := None
ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := supportScalaVersions
ThisBuild / githubWorkflowBuildSbtStepPreamble := Nil

lazy val root =
  tlCrossRootProject
    .aggregate(objects, circe, manifests, docs)
    .enablePlugins(AutomateHeaderPlugin)

lazy val circeVersion = "0.14.1"

val rootDir = Def.setting((ThisBuild / baseDirectory).value)

lazy val objects = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .settings(
    name := "k8s-objects",
    libraryDependencies ++= Seq(
      // "io.circe" %%% "circe-core" % circeVersion
    ),
    k8sUnmanagedTarget := rootDir.value / "objects" / "src" / "main" / "scala",
    // k8sManagedTarget := rootDir.value / "objects" / "target" / "src_managed" / "main" / "scala",
    kubernetesVersion := "1.25.0"
  )
  .enablePlugins(NoPublishPlugin, KubernetesObjectGeneratorPlugin)

lazy val circe = crossProject(JVMPlatform, JSPlatform)
  .settings(
    name := "k8s-circe",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % circeVersion
    )
  )
  .dependsOn(objects)

lazy val manifests = crossProject(JVMPlatform)
  .in(file("lib"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "k8s-manifests",
    libraryDependencies ++= Seq(
      "com.goyeau" %% "kubernetes-client" % "0.8.1"
    )
  )

lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)
