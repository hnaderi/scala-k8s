import dev.hnaderi.k8s.generator.KubernetesJsonPointerGeneratorPlugin
import dev.hnaderi.k8s.generator.KubernetesScalacheckGeneratorPlugin
import sbtcrossproject.CrossProject

ThisBuild / tlBaseVersion := "0.8"

ThisBuild / organization := "dev.hnaderi"
ThisBuild / organizationName := "Hossein Naderi"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("hnaderi", "Hossein Naderi")
)

val scala212 = "2.12.17"
val scala213 = "2.13.10"
val scala3 = "3.2.2"
val PrimaryJava = JavaSpec.temurin("8")
val LTSJava = JavaSpec.temurin("17")

val supportScalaVersions = Seq(scala212, scala213, scala3)

ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := supportScalaVersions
ThisBuild / githubWorkflowJavaVersions := Seq(PrimaryJava, LTSJava)
ThisBuild / kubernetesVersion := "1.26.1"
ThisBuild / jsEnv := {
  import org.scalajs.jsenv.nodejs.NodeJSEnv
  new NodeJSEnv(NodeJSEnv.Config().withArgs(List("--max-old-space-size=6144")))
}

enablePlugins(AutomateHeaderPlugin)

lazy val root =
  tlCrossRootProject
    .aggregate(
      objects,
      objectsTest,
      clientTest,
      client,
      http4s,
      zio,
      sttp,
      codecTest,
      circe,
      `spray-json`,
      `play-json`,
      json4s,
      `zio-json`,
      jawn,
      manifests,
      scalacheck,
      docs,
      unidocs,
      example
    )
    .settings(
      name := "scala-k8s"
    )

lazy val circeVersion = "0.14.2"
lazy val munitVersion = "0.7.29"

val rootDir = Def.setting((ThisBuild / baseDirectory).value)

def module(mname: String): CrossProject => CrossProject =
  _.in(file(s"modules/$mname"))
    .settings(
      name := s"scala-k8s-$mname"
    )

lazy val objects = module("objects") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "data models for kubernetes",
      k8sUnmanagedTarget := rootDir.value / "modules" / "objects" / "src" / "main" / "scala"
    )
    .enablePlugins(KubernetesObjectGeneratorPlugin)
}

lazy val client = module("client") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "client core for kubernetes",
      k8sUnmanagedTarget := rootDir.value / "modules" / "client" / "src" / "main" / "scala"
    )
    .dependsOn(objects)
    .enablePlugins(KubernetesJsonPointerGeneratorPlugin)
}

lazy val http4s = module("http4s") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "http4s based client for kubernetes",
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-ember-client" % "0.23.18",
        "org.typelevel" %%% "jawn-fs2" % "2.4.0"
      )
    )
    .dependsOn(client, jawn)
}

lazy val sttp = module("sttp") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "sttp based client for kubernetes",
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.client3" %%% "core" % "3.8.11"
      )
    )
    .dependsOn(client, jawn)
}

lazy val zio = module("zio") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "zio-http based client for kubernetes",
      libraryDependencies ++= Seq(
        "io.d11" %%% "zhttp" % "2.0.0-RC10",
        "dev.zio" %%% "zio-json" % "0.4.2"
      )
    )
    .dependsOn(client, `zio-json`)
}

lazy val scalacheck = module("scalacheck") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "scalacheck generators for kubernetes data models",
      k8sUnmanagedTarget := rootDir.value / "modules" / "scalacheck" / "src" / "main" / "scala",
      libraryDependencies ++= Seq(
        "org.scalacheck" %%% "scalacheck" % "1.17.0"
      )
    )
    .dependsOn(objects)
    .enablePlugins(KubernetesScalacheckGeneratorPlugin)
}

lazy val objectsTest = module("objects-test") {
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "internal tests for scala-k8s objects",
      libraryDependencies ++= Seq(
        "org.scalameta" %%% "munit" % munitVersion % Test
      )
    )
    .dependsOn(objects)
    .enablePlugins(NoPublishPlugin)
}

lazy val clientTest = module("client-test") {
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "internal tests for scala-k8s client",
      libraryDependencies ++= Seq(
        "org.scalameta" %%% "munit" % munitVersion % Test
      )
    )
    .dependsOn(client)
    .enablePlugins(NoPublishPlugin)
}

lazy val codecTest = module("codec-test") {
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .settings(
      name := "scala-k8s-codec-test",
      description := "internal codec tests for scala-k8s objects",
      libraryDependencies ++= Seq(
        "org.scalameta" %%% "munit" % munitVersion,
        "org.scalameta" %%% "munit-scalacheck" % munitVersion
      )
    )
    .dependsOn(scalacheck)
    .enablePlugins(NoPublishPlugin)
}

lazy val circe = module("circe") {
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "circe codecs for kubernetes data models",
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core" % circeVersion
      )
    )
    .dependsOn(objects)
    .dependsOn(codecTest % Test)
}

lazy val `spray-json` = module("spray-json") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "spray-json codecs for kubernetes data models",
      libraryDependencies ++= Seq(
        "io.spray" %%% "spray-json" % "1.3.6"
      )
    )
    .dependsOn(objects)
    .dependsOn(codecTest % Test)
}

lazy val `play-json` = module("play-json") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "play-json codecs for kubernetes data models",
      libraryDependencies ++= Seq(
        ("com.typesafe.play" %%% "play-json" % "2.9.4")
          .cross(CrossVersion.for3Use2_13)
      )
    )
    .dependsOn(objects)
    .dependsOn(codecTest % Test)
}

lazy val json4s = module("json4s") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "json4s codecs for kubernetes data models",
      libraryDependencies ++= Seq(
        "org.json4s" %%% "json4s-ast" % "4.0.6"
      )
    )
    .dependsOn(objects)
}

lazy val `zio-json` = module("zio-json") {
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "zio-json codecs for kubernetes data models",
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio-json" % "0.4.2"
      )
    )
    .dependsOn(objects)
    .dependsOn(codecTest % Test)
}

lazy val jawn = module("jawn") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "jawn facade for kubernetes data models parsing",
      libraryDependencies ++= Seq(
        "org.typelevel" %%% "jawn-parser" % "1.4.0"
      )
    )
    .dependsOn(objects)
}

lazy val manifests = module("manifests") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "kubernetes manifests utilities",
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-yaml" % circeVersion,
        "io.circe" %%% "circe-parser" % circeVersion
      )
    )
    .dependsOn(circe)
}

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteRelatedProjects := Seq(
      "Kubernetes" -> url("https://github.com/kubernetes/kubernetes"),
      "sbt k8s" -> url("https://github.com/hnaderi/sbt-k8s"),
      TypelevelProject.Http4s,
      TypelevelProject.Fs2,
      TypelevelProject.Scalacheck,
      "ZIO" -> url("https://github.com/zio/zio"),
      "ZIO-http" -> url("https://github.com/zio/zio-http"),
      "ZIO-json" -> url("https://github.com/zio/zio-json"),
      "sttp" -> url("https://sttp.softwaremill.com"),
      "Circe" -> url("https://github.com/circe/circe"),
      "Spray json" -> url("https://github.com/spray/spray-json"),
      "Play json" -> url("https://github.com/playframework/play-json"),
      "Json4s" -> url("https://github.com/json4s/json4s"),
      "Jawn" -> url("https://github.com/typelevel/jawn")
    ),
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-circe" % "0.23.18",
      "com.softwaremill.sttp.client3" %%% "circe" % "3.8.11"
    )
  )
  .dependsOn(http4s.jvm, sttp.jvm, circe.jvm, manifests.jvm)

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    name := "scala-k8s-docs",
    description := "unified docs for scala-k8s",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
      objects.jvm,
      client.jvm,
      http4s.jvm,
      zio.jvm,
      sttp.jvm,
      circe.jvm,
      `spray-json`.jvm,
      `play-json`.jvm,
      json4s.jvm,
      `zio-json`.jvm,
      jawn.jvm,
      manifests.jvm,
      scalacheck.jvm
    )
  )

lazy val example = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-circe" % "0.23.18",
      "com.softwaremill.sttp.client3" %%% "circe" % "3.8.11"
    )
  )
  .dependsOn(http4s, circe, zio, sttp)
  .enablePlugins(NoPublishPlugin)

def addAlias(name: String)(tasks: String*) =
  addCommandAlias(name, tasks.mkString(" ;"))

addAlias("commit")(
  "reload",
  "clean",
  "scalafmtCheckAll",
  "scalafmtSbtCheck",
  "headerCheckAll",
  "githubWorkflowCheck",
  "+test"
)

addAlias("precommit")(
  "reload",
  "scalafmtAll",
  "scalafmtSbt",
  "headerCreateAll",
  "githubWorkflowGenerate",
  "+test"
)
