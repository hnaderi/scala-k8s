## sbt-k8s

### Usage

This library is currently available for Scala binary versions 2.12, 2.13 and 3.1 on JVM. 
sbt plugins are also available for sbt 1.x

To use the latest version of plugins, include the following in your `project/plugins.sbt`:

```scala
addSbtPlugin("dev.hnaderi" % "sbt-k8s" % "@VERSION@") // everything
addSbtPlugin("dev.hnaderi" % "sbt-k8s-manifest" % "@VERSION@") // just manifest generation and objects
addSbtPlugin("dev.hnaderi" % "sbt-k8s-cookbook" % "@VERSION@") // cookbook recipes
```

To use the latest version of library, include the following in your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "dev.hnaderi" %% "k8s-objects" % "@VERSION@"
)
```
