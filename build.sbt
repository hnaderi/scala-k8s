import dev.hnaderi.k8s.generator.KubernetesJsonPointerGeneratorPlugin
import dev.hnaderi.k8s.generator.KubernetesScalacheckGeneratorPlugin
import sbtcrossproject.CrossProject

ThisBuild / tlBaseVersion := "0.22"

ThisBuild / organization := "dev.hnaderi"
ThisBuild / organizationName := "Hossein Naderi"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("hnaderi", "Hossein Naderi")
)

val scala212 = "2.12.20"
val scala213 = "2.13.15"
val scala3 = "3.3.3"
val PrimaryJava = JavaSpec.temurin("11")
val LTSJava = JavaSpec.temurin("17")

val supportScalaVersions = Seq(scala212, scala213, scala3)

ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / tlJdkRelease := Some(11)
ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := supportScalaVersions
ThisBuild / githubWorkflowJavaVersions := Seq(PrimaryJava, LTSJava)
ThisBuild / githubWorkflowBuildMatrixFailFast := Some(false)
// This job is used as a sign that all build jobs have been successful and is used by mergify
ThisBuild / githubWorkflowAddedJobs += WorkflowJob(
  id = "post-build",
  name = "post build",
  needs = List("build"),
  steps = List(
    WorkflowStep.Run(
      commands = List("echo success!"),
      name = Some("post build")
    )
  ),
  scalas = Nil,
  javas = Nil
)
ThisBuild / kubernetesVersion := "1.32.1"
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
      javaSSL,
      http4s,
      http4sEmber,
      http4sNetty,
      http4sBlaze,
      http4sJDK,
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
      exampleJVM,
      exampleCrossPlatform
    )
    .settings(
      name := "scala-k8s"
    )

lazy val circeVersion = "0.14.8"
lazy val munitVersion = "1.0.0-M11"

val rootDir = Def.setting((ThisBuild / baseDirectory).value)

def module(mname: String): CrossProject => CrossProject =
  _.in(file(s"modules/$mname"))
    .settings(
      name := s"scala-k8s-$mname"
    )

def example(mname: String): CrossProject => CrossProject =
  _.in(file(s"examples/$mname"))
    .settings(
      name := s"scala-k8s-example-$mname"
    )
    .enablePlugins(NoPublishPlugin)

lazy val objects = module("objects") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "data models for kubernetes",
      k8sUnmanagedTarget := rootDir.value / "modules" / "objects" / "src" / "main" / "scala",
      buildInfoKeys := Seq[BuildInfoKey](
        version,
        scalaVersion,
        kubernetesVersion
      ),
      buildInfoPackage := "dev.hnaderi.k8s",
      buildInfoOptions ++= Seq(BuildInfoOption.ConstantValue)
    )
    .enablePlugins(KubernetesObjectGeneratorPlugin, BuildInfoPlugin)
}

lazy val client = module("client") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "client core for kubernetes",
      k8sUnmanagedTarget := rootDir.value / "modules" / "client" / "src" / "main" / "scala"
    )
    .dependsOn(objects, manifests)
    .enablePlugins(KubernetesJsonPointerGeneratorPlugin)
}

lazy val javaSSL = module("java-ssl") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "java ssl for kubernetes config",
      libraryDependencies ++= Seq(
        "org.bouncycastle" % "bcpkix-jdk18on" % "1.80"
      )
    )
    .dependsOn(client)
}

lazy val http4s = module("http4s") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "http4s based client for kubernetes",
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-client" % "0.23.30"
      )
    )
    .dependsOn(client, jawn)
    .jvmConfigure(_.dependsOn(javaSSL.jvm))
}

lazy val http4sEmber = module("http4s-ember") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "http4s ember based client for kubernetes",
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-ember-client" % "0.23.30"
      )
    )
    .dependsOn(http4s)
}
lazy val http4sNetty = module("http4s-netty") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "http4s netty based client for kubernetes",
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-netty-client" % "0.5.22"
      )
    )
    .dependsOn(http4s)
}
lazy val http4sBlaze = module("http4s-blaze") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "http4s blaze based client for kubernetes",
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-blaze-client" % "0.23.17"
      )
    )
    .dependsOn(http4s)
}

lazy val http4sJDK = module("http4s-jdk") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "http4s jdk-client based client for kubernetes",
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-jdk-http-client" % "0.10.0"
      )
    )
    .dependsOn(http4s)
}

lazy val sttp = module("sttp") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "sttp based client for kubernetes",
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.client3" %%% "core" % "3.9.8"
      )
    )
    .dependsOn(client, jawn)
    .jvmConfigure(_.dependsOn(javaSSL.jvm))
}

lazy val zio = module("zio") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "zio-http based client for kubernetes",
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-http" % "3.0.0-RC4",
        "dev.zio" %%% "zio-json" % "0.7.5"
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
        "org.scalacheck" %%% "scalacheck" % "1.17.1"
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
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
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
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
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
        ("org.playframework" %%% "play-json" % "3.0.4")
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
        "org.json4s" %%% "json4s-ast" % "4.0.7"
      )
    )
    .dependsOn(objects)
    .dependsOn(codecTest % Test)
}

lazy val `zio-json` = module("zio-json") {
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "zio-json codecs for kubernetes data models",
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio-json" % "0.7.5"
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
        "org.typelevel" %%% "jawn-parser" % "1.5.1"
      )
    )
    .dependsOn(objects)
}

lazy val manifests = module("manifests") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      description := "kubernetes manifests utilities",
      libraryDependencies ++= Seq(
        "dev.hnaderi" %%% "yaml4s-backend" % "0.2.2"
      )
    )
    .dependsOn(objects)
    .dependsOn(codecTest % Test)
}

lazy val docs = project
  .in(file("site"))
  .enablePlugins(ScalaK8sWebsite)
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %%% "http4s-circe" % "0.23.30",
      "com.softwaremill.sttp.client3" %%% "circe" % "3.10.2"
    )
  )
  .dependsOn(http4sEmber.jvm, sttp.jvm, circe.jvm, manifests.jvm)

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
      http4sEmber.jvm,
      http4sNetty.jvm,
      http4sBlaze.jvm,
      http4sJDK.jvm,
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

lazy val exampleJVM = example("jvm") {
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-circe" % "0.23.30",
        "com.softwaremill.sttp.client3" %%% "circe" % "3.10.2"
      )
    )
    .dependsOn(http4sNetty, http4sEmber, circe, zio, sttp)
}

lazy val exampleCrossPlatform = example("cross-platform") {
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s" %%% "http4s-circe" % "0.23.30"
      )
    )
    .jsSettings(
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
      Compile / npmDependencies ++= Seq("js-yaml" -> "4.1.0")
      // scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
    )
    .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
    .nativeSettings(
      libraryDependencies += "com.armanbilge" %%% "epollcat" % "0.1.6",
      envVars ++= Map("S2N_DONT_MLOCK" -> "1")
    )
    .dependsOn(http4sEmber, circe)
}

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
