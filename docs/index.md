## sbt-k8s

### Usage

This library is currently available for Scala binary versions 2.12, 2.13 and 3.1 on JVM/JS/Native. 

To use the latest version of library, include the following in your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "dev.hnaderi" %% "scala-k8s-objects" % "@VERSION@", // JVM, JS, NATIVE
  "dev.hnaderi" %% "scala-k8s-circe" % "@VERSION@", // JVM, JS
  "dev.hnaderi" %% "scala-k8s-manifests" % "@VERSION@" // JVM
)
```
