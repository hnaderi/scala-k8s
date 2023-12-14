<p align="center">
  <img src="https://raw.githubusercontent.com/kubernetes/kubernetes/master/logo/logo.png" height="100px" alt="kubernetes icon" />
  <br/>
  <strong>Scala K8S</strong>
  <i>Kubernetes client, data models and typesafe manifest generation for scala, scalajs, and scala native</i>
</p>

<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

![Kubernetes version](https://img.shields.io/badge/Kubernetes-v1.29.0-blue?style=flat-square&logo=kubernetes&logoColor=white)
[![scala-k8s-objects Scala version support](https://index.scala-lang.org/hnaderi/scala-k8s/scala-k8s-objects/latest-by-scala-version.svg?style=flat-square)](https://index.scala-lang.org/hnaderi/scala-k8s/scala-k8s-objects)
[![javadoc](https://javadoc.io/badge2/dev.hnaderi/scala-k8s-docs_3/scaladoc.svg?style=flat-square)](https://javadoc.io/doc/dev.hnaderi/scala-k8s-docs_3)  
<img alt="GitHub Workflow Status" src="https://img.shields.io/github/actions/workflow/status/hnaderi/scala-k8s/ci.yml?style=flat-square">
<img alt="GitHub" src="https://img.shields.io/github/license/hnaderi/scala-k8s?style=flat-square">
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat-square&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## What
This library provides a full blown extensible client that you can use to interact directly with kubernetes API server, to create operators or accomplish other automation tasks, also you can use it to create or manipulate manifests in scala.

## Why
Kubernetes spec is large enough to fit in one's brain, and YAML and helm are a joke! instead of using different tooling and languages with no to little IDE and compiler support, you can use this library to have the latest kubernetes API spec under your tool belt, in scala!
so the most complex templates are just simple functions, and you can use whatever abstraction you like to create objects; and create manifests easily.  
for easy to use recipes and integration with sbt, visit [this project](https://github.com/hnaderi/sbt-k8s)  

## Goals
- to become the defacto k8s integration library in all scala ecosystems 

## Design principles
- As extensible as possible
- As dependency free as possible
- As Un-opinionated as possible
- Provide seamless integrations
- All specs are generated from the spec directly and will be in sync with kubernetes all the time

## Getting started

This library is currently available for Scala binary versions 2.12, 2.13 and 3.2 on JVM/JS/Native.  
This library is architecured in a microkernel fashion and all the main kubernetes stuff are implemented/generated in pure scala, and integration modules are provided separately.  
main modules are:

- `objects` raw kubernetes objects, which has no dependency
- `client` raw kubernetes client and requests, requests can also be extended in user land easily!

``` scala
libraryDependencies ++= Seq(
  "dev.hnaderi" %% "scala-k8s-objects" % "@VERSION@", // JVM, JS, Native ; raw k8s objects
  "dev.hnaderi" %% "scala-k8s-client" % "@VERSION@", // JVM, JS, Native ; k8s client kernel and requests
  )
```

The following integrations are currently available:

```scala
libraryDependencies ++= Seq(
  "dev.hnaderi" %% "scala-k8s-http4s-ember" % "@VERSION@", // JVM, JS, Native ; http4s ember client integration
  "dev.hnaderi" %% "scala-k8s-http4s-netty" % "@VERSION@", // JVM ; http4s netty client integration
  "dev.hnaderi" %% "scala-k8s-http4s-blaze" % "@VERSION@", // JVM; http4s blaze client integration
  "dev.hnaderi" %% "scala-k8s-http4s-jdk" % "@VERSION@", // JVM; http4s jdk-client integration
  "dev.hnaderi" %% "scala-k8s-http4s" % "@VERSION@", // JVM, JS, Native ; http4s core and fs2 integration
  "dev.hnaderi" %% "scala-k8s-zio" % "@VERSION@", // JVM ; ZIO native integration using zio-http and zio-json 
  "dev.hnaderi" %% "scala-k8s-sttp" % "@VERSION@", // JVM, JS, Native ; sttp integration using jawn parser
  "dev.hnaderi" %% "scala-k8s-circe" % "@VERSION@", // JVM, JS ; circe integration
  "dev.hnaderi" %% "scala-k8s-json4s" % "@VERSION@", // JVM, JS, Native; json4s integration
  "dev.hnaderi" %% "scala-k8s-spray-json" % "@VERSION@", // JVM ; spray-json integration
  "dev.hnaderi" %% "scala-k8s-play-json" % "@VERSION@", // JVM ; play-json integration
  "dev.hnaderi" %% "scala-k8s-zio-json" % "@VERSION@", // JVM, JS ; zio-json integration
  "dev.hnaderi" %% "scala-k8s-jawn" % "@VERSION@", // JVM, JS, Native ; jawn integration
  "dev.hnaderi" %% "scala-k8s-manifests" % "@VERSION@", // JVM, JS, Native ; yaml manifest reading and generation
  "dev.hnaderi" %% "scala-k8s-scalacheck" % "@VERSION@" // JVM, JS, Native; scalacheck instances
)
```

visit [project site](https://projects.hnaderi.dev/scala-k8s) to see more tutorials and docs, 
also please drop a ⭐ if this project interests you. I need encouragement.

## sbt integration
see [this project](https://github.com/hnaderi/sbt-k8s)

## Future plans
- more integrations (akka-http, ...)!
