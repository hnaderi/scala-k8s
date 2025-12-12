lazy val circeVersion = "0.14.2"

ThisBuild / scalaVersion := "2.12.21"
ThisBuild / scalacOptions += "-Ypartial-unification"

lazy val generator = project.settings(
  name := "k8s-objects-generator",
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
)

lazy val plugin = project.dependsOn(generator).enablePlugins(SbtPlugin)

lazy val root = project.in(file(".")).dependsOn(plugin)
