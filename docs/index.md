## scala-k8s

### Usage

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
  "dev.hnaderi" %% "scala-k8s-http4s" % "@VERSION@", // JVM, JS, Native ; http4s and fs2 integration
  "dev.hnaderi" %% "scala-k8s-zio" % "@VERSION@", // JVM ; ZIO native integration using zio-http and zio-json 
  "dev.hnaderi" %% "scala-k8s-circe" % "@VERSION@", // JVM, JS ; circe integration
  "dev.hnaderi" %% "scala-k8s-json4s" % "@VERSION@", // JVM, JS, Native; json4s integration
  "dev.hnaderi" %% "scala-k8s-spray-json" % "@VERSION@", // JVM ; spray-json integration
  "dev.hnaderi" %% "scala-k8s-play-json" % "@VERSION@", // JVM ; play-json integration
  "dev.hnaderi" %% "scala-k8s-zio-json" % "@VERSION@", // JVM, JS ; zio-json integration
  "dev.hnaderi" %% "scala-k8s-jawn" % "@VERSION@", // JVM, JS, Native ; jawn integration
  "dev.hnaderi" %% "scala-k8s-manifests" % "@VERSION@", // JVM ; yaml manifest generation
  "dev.hnaderi" %% "scala-k8s-scalacheck" % "@VERSION@" // JVM, JS, Native; scalacheck instances
)
```

## Manifest and object generation
first off, we'll import the following
```scala mdoc
import dev.hnaderi.k8s._  // base packages
import dev.hnaderi.k8s.implicits._  // implicit coversions and helpers
import dev.hnaderi.k8s.manifest._  // manifest syntax
``` 
every other object definition is under kubernetes packages `io.k8s` as specified in the spec, you should rely on 
IDE auto import for those.

```scala mdoc:invisible
import io.k8s.api.apps.v1.Deployment
import io.k8s.api.apps.v1.DeploymentSpec
import io.k8s.api.core.v1.ConfigMap
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.PodSpec
import io.k8s.api.core.v1.PodTemplateSpec
import io.k8s.api.core.v1.Service
import io.k8s.api.core.v1.ServicePort
import io.k8s.api.core.v1.ServiceSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.LabelSelector
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

import java.nio.file.Paths
import java.io.File
```

Now we can define any kubernetes object

### ConfigMap example

```scala mdoc:silent
val config = ConfigMap(
  metadata = ObjectMeta(
    name = "example",
    namespace = "staging",
    labels = Map(
      Labels.name("example"),
      Labels.instance("one")
    )
  ),
  data = DataMap(
    "some config" -> "some value",
    "config file" -> Data.file(".envrc")
  ),
  binaryData = DataMap.binary(
    "blob" -> Data.file(".gitignore"),
    "blob2" -> Paths.get(".scalafmt.conf"),
    "other inline data" -> "some other data"
  )
)
```

or even from a whole directory, like `kubectl`

```scala mdoc:silent
val config2 = ConfigMap(
  data = DataMap.fromDir(new File("objects/src/test/resources/data"))
)
```

### Deployment example

```scala mdoc:silent
val deployment = Deployment(
  metadata = ObjectMeta(
    name = "example",
    namespace = "staging"
  ),
  spec = DeploymentSpec(
    selector = LabelSelector(matchLabels = Map("app" -> "example")),
    template = PodTemplateSpec(
      spec = PodSpec(
        containers = Seq(
          Container(
            name = "abc",
            image = "hello-world:latest"
          )
        )
      )
    )
  )
)
```

### Service example

```scala mdoc:silent
val service = Service(
  metadata = ObjectMeta(
    name = "example",
    namespace = ""
  ),
  spec = ServiceSpec(
    selector = Map("app" -> "example"),
    ports = Seq(ServicePort(port = 80, targetPort = 8080, name = "http"))
  )
)
```

### Manifest example

Now you can merge all of your kubernetes resource definitions in to one manifest
```scala mdoc:silent
val all : Seq[KObject] = Seq(service, config, deployment)
val manifest = all.asManifest
```

which will output like this

```scala mdoc
println(manifest)
```

### Helpers
You can also use helpers to manipulate data models easily

```scala mdoc:silent
val config3 = config
  .addData("new-key" -> "new value")
  .withImmutable(true)
  .mapMetadata(_.withName("new-config").withNamespace("production"))
```

All fields have the following helper methods:

* `withFieldName` that acts like a setter
* `addFieldName` for lists and maps, adds new values
* `mapFieldName` transforms using a function


```scala mdoc
println(config3.asManifest)
```

## Client

Currently clients does not support direct TLS connection, so you must use `kubectl proxy`, and use proxy address in clients.

### Http4s based client
http4s based client support all APIs.

```scala mdoc:to-string
import cats.effect._
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import io.circe.Json
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder

val buildClient =
  EmberClientBuilder
    .default[IO]
    .build
    .map(Http4sKubernetesClient[IO, Json]("http://localhost:8001", _))
 
val getNodes = buildClient.use(APIs.nodes.list.send)

val watchNodes = fs2.Stream.resource(buildClient).flatMap(APIs.nodes.list.listen)

val getConfigMaps = 
  buildClient.use(client=>
    APIs
      .namespace("kube-system")
      .configmaps
      .get("kube-proxy")
      .send(client)
  )
```

### ZIO based client
Currently, ZIO based client does not support streaming watch APIs, it will support as soon as [zio-http supports streaming responses](https://github.com/hnaderi/scala-k8s/issues/17)
```scala
import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.ZIOKubernetesClient

val client = ZIOKubernetesClient.make("http://localhost:8001")
val nodes = ZIOKubernetesClient.send(APIs.nodes.list)
```

### Working with requests
Requests are plain data, so you can manipulate or pass them like any normal data

```scala mdoc
import dev.hnaderi.k8s.client.APIs

val sysConfig = APIs
  .namespace("kube-system")
  .configmaps

val defaultConfig = sysConfig.copy(namespace = "default")
```

### Implementing new requests
you can also implement your own requests easily, however if you need a request that is widely used and is standard, please open an issue or better, a pull request, so everyone can use it.

```scala mdoc
import dev.hnaderi.k8s.client._

type CustomResource = String
case class MyCustomRequest(name: String) extends GetRequest[CustomResource](
  s"/apis/my.custom-resource.io/$name"
)
```
