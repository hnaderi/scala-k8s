# Getting started

This library is available for Scala binary versions 2.12, 2.13 and 3.3 on JVM/JS/Native.
It is designed in a microkernel fashion: all Kubernetes objects are implemented in pure Scala with no dependencies, and integration modules are provided separately.

## Core modules

- `objects` raw Kubernetes objects, no dependencies
- `client` raw Kubernetes client and requests, extensible in user land

``` scala
libraryDependencies ++= Seq(
  "dev.hnaderi" %% "scala-k8s-objects" % "@VERSION@",
  "dev.hnaderi" %% "scala-k8s-client"  % "@VERSION@"
)
```

## Integration modules

```scala
libraryDependencies ++= Seq(
  "dev.hnaderi" %% "scala-k8s-http4s-ember" % "@VERSION@", // JVM, JS, Native ; http4s ember client integration
  "dev.hnaderi" %% "scala-k8s-http4s-netty" % "@VERSION@", // JVM ; http4s netty client integration
  "dev.hnaderi" %% "scala-k8s-http4s-blaze" % "@VERSION@", // JVM; http4s blaze client integration
  "dev.hnaderi" %% "scala-k8s-http4s-jdk"   % "@VERSION@", // JVM; http4s jdk-client integration
  "dev.hnaderi" %% "scala-k8s-http4s"       % "@VERSION@", // JVM, JS, Native ; http4s core and fs2 integration
  "dev.hnaderi" %% "scala-k8s-zio"          % "@VERSION@", // JVM ; ZIO native integration using zio-http and zio-json
  "dev.hnaderi" %% "scala-k8s-sttp"         % "@VERSION@", // JVM, JS, Native ; sttp integration using jawn parser
  "dev.hnaderi" %% "scala-k8s-circe"        % "@VERSION@", // JVM, JS ; circe integration
  "dev.hnaderi" %% "scala-k8s-json4s"       % "@VERSION@", // JVM, JS, Native; json4s integration
  "dev.hnaderi" %% "scala-k8s-spray-json"   % "@VERSION@", // JVM ; spray-json integration
  "dev.hnaderi" %% "scala-k8s-play-json"    % "@VERSION@", // JVM ; play-json integration
  "dev.hnaderi" %% "scala-k8s-zio-json"     % "@VERSION@", // JVM, JS ; zio-json integration
  "dev.hnaderi" %% "scala-k8s-jawn"         % "@VERSION@", // JVM, JS, Native ; jawn integration
  "dev.hnaderi" %% "scala-k8s-manifests"    % "@VERSION@", // JVM, JS, Native ; yaml manifest reading and generation
  "dev.hnaderi" %% "scala-k8s-scalacheck"   % "@VERSION@"  // JVM, JS, Native; scalacheck instances
)
```

## SBT integration
[sbt-k8s](https://github.com/hnaderi/sbt-k8s) is a companion sbt plugin that generates Kubernetes manifests directly from your build.
It integrates with the Native Packager Docker plugin to automatically derive deployment configuration from your project (image name, version, resource limits, environment variables, and service definitions) and writes the result to `target/k8s/manifest.yml` via the `k8sManifestGen` task.
For projects that want full control, `k8sManifestObjects` accepts arbitrary scala-k8s objects.
