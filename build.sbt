ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "dev.hnaderi"
ThisBuild / organizationName := "Hossein Naderi"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("hnaderi", "Hossein Naderi")
)

ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / scalaVersion := "2.12.16"

lazy val root = project.aggregate(plugin, docs)

lazy val plugin = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin, SbtPlugin)
  .settings(
    name := "sbt-k8s",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.goyeau" %% "kubernetes-client" % "0.8.1"
    ),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.2.8" // set minimum sbt version
      }
    }
  )

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")

lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)
