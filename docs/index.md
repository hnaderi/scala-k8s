# scala-k8s

## Usage

This library is currently available for Scala binary versions 2.12, 2.13 and 3.2 on JVM/JS/Native.  
This library is designed in a microkernel fashion and all the main kubernetes stuff are implemented/generated in pure scala, and integration modules are provided separately.  
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

# Manifest and object generation
first off, we'll import the following
```scala mdoc
import dev.hnaderi.k8s._  // base packages
import dev.hnaderi.k8s.implicits._  // implicit conversions and helpers
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

## ConfigMap example

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

## Deployment example

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

## Service example

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

## Manifest example

Now you can merge all of your kubernetes resource definitions into one manifest
```scala mdoc:silent
val all : Seq[KObject] = Seq(service, config, deployment)
val manifest = all.asManifest
```

which will output like this

```scala mdoc
println(manifest)
```

## Helpers
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

## Custom resources

For defining custom resource, for instance CRDs or non-standard kubernetes resources that aren't part of the kubernetes spec:

1. First you need to model your resources:

```scala mdoc
case class CustomResourceSpec(
  someKey: String,
  // other fields
)

case class CustomResource(
  spec: CustomResourceSpec
  // other fields
)

```

2. Then you need to define instances for @:api(dev.hnaderi.k8s.utils.Encoder) and @:api(dev.hnaderi.k8s.utils.Decoder), so your data model becomes serializable.

```scala mdoc
import dev.hnaderi.k8s.utils.*
// in the companion objects
object CustomResourceSpec{
    implicit val decoder: Decoder[CustomResourceSpec] = new Decoder[CustomResourceSpec] {
      def apply[T : Reader](t: T): Either[String, CustomResourceSpec] = for {
          obj <- ObjectReader(t)
          value <- obj.read[String]("someKey")
          // Other fields
      } yield CustomResourceSpec(value)
    }

    implicit val encoder : Encoder[CustomResourceSpec] = new Encoder[CustomResourceSpec] {
        def apply[T : Builder](o: CustomResourceSpec) : T = {
          val obj = ObjectWriter[T]()
          obj
            .write("someKey", o.someKey)
            // Other fields
            .build
        }
    }
}

object CustomResource{
    implicit val encoder : Encoder[CustomResource] = new Encoder[CustomResource] {
        def apply[T : Builder](o: CustomResource) : T = {
          val obj = ObjectWriter[T]()
          obj
            .write("kind", "MyCustomResource")
            .write("apiVersion", "example.com/v1CustomResoure")
            .write("spec", o.spec)
            // Other fields
            .build
        }
    }

    implicit val decoder: Decoder[CustomResource] = new Decoder[CustomResource] {
      def apply[T : Reader](t: T): Either[String, CustomResource] = for {
          obj <- ObjectReader(t)
          spec <- obj.read[CustomResourceSpec]("spec")
          // Other fields
      } yield CustomResource(spec)
    }
}
```

```scala mdoc:invisible
case class CustomResourceList()
object CustomResourceList{
  implicit val decoder: Decoder[CustomResourceList] = new Decoder[CustomResourceList] {
    def apply[T : Reader](t: T): Either[String, CustomResourceList] = Left("")
  }
}
```

# Client

Scala k8s provides a kubernetes client built on top of a generic http client, this allows us to use different http clients based on project ecosystem and other considerations.
Being modular and not depending on a specific environment opens the door to extensibility, and also means it does restrict you in any imaginable way and you can choose whatever you want, configure however you want!  

The following are some examples that use `kubectl proxy` for simplicity sake.

## Http4s based client
http4s based client support all APIs.

```scala mdoc:to-string
import cats.effect._
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.client.http4s.EmberKubernetesClient
import io.circe.Json
import org.http4s.circe._

val buildClient = EmberKubernetesClient[IO].defaultConfig[Json]
 
val getNodes = buildClient.use(APIs.nodes.list().send)

val watchNodes = fs2.Stream.resource(buildClient).flatMap(APIs.nodes.list().listen)

val getConfigMaps = 
  buildClient.use(client=>
    APIs
      .namespace("kube-system")
      .configmaps
      .get("kube-proxy")
      .send(client)
  )
```

## ZIO based client
Currently, ZIO based client does not support streaming watch APIs, it will support as soon as [zio-http supports streaming responses](https://github.com/hnaderi/scala-k8s/issues/17)
```scala
import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.ZIOKubernetesClient

val client = ZIOKubernetesClient.make("http://localhost:8001")
val nodes = ZIOKubernetesClient.send(APIs.nodes.list())
```

## Sttp based client
```scala mdoc:compile-only
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client.APIs
import dev.hnaderi.k8s.client.SttpJdkURLClientBuilder
import sttp.client3.circe._

val client = SttpJdkURLClientBuilder.defaultConfig[Json]

val nodes = APIs.nodes.list().send(client)
nodes.body.items.flatMap(_.metadata).flatMap(_.name).foreach(println)
```

# API calls
## Working with requests
Requests are plain data, so you can manipulate or pass them like any normal data

```scala mdoc
import dev.hnaderi.k8s.client.APIs

val sysConfig = APIs
  .namespace("kube-system")
  .configmaps

val defaultConfig = sysConfig.copy(namespace = "default")
```

## Advanced requests

For doing simple strategical merge patches:
```scala mdoc:to-string
val patch1 = APIs
  .namespace("default")
  .configmaps
  .patch(
    "test",
    ConfigMap(metadata = ObjectMeta(labels = Map("new" -> "label")))
  )
```


For doing [Json patch](https://www.rfc-editor.org/rfc/rfc6902):
```scala mdoc:to-string
// You need to import pointer instances
import dev.hnaderi.k8s.client.implicits._

val patch2 = APIs
  .namespace("default")
  .configmaps
  .jsonPatch("test")(
    JsonPatch[ConfigMap].builder
      .add(_.metadata.labels.at("new"), "label")
      .move(_.metadata.labels.at("a"), _.metadata.labels.at("b"))
      .remove(_.data.at("to-delete"))
  )
```


Server side apply:
```scala mdoc:to-string
val patch3 = APIs
  .namespace("default")
  .configmaps
  .serverSideApply("test", ConfigMap(), fieldManager = "my-operator")
```


Or [json merge patches](https://www.rfc-editor.org/rfc/rfc7386):
```scala mdoc:to-string
val patch4 = APIs
  .namespace("default")
  .configmaps
  .patch(
    "test",
    ConfigMap(metadata = ObjectMeta(labels = Map("new" -> "label"))),
    patch = PatchType.Merge
  )
```


Your own custom type merge, for times that you need all the control:
```scala mdoc:to-string
type CustomMerge = String // Your custom object to be send to kubernetes
val customMergeObject : CustomMerge = ""
// You need to define encoder for your type
// implicit val customMergeObjectEncoder : Encoder[CustomMerge] = ???

val patch5 = APIs
  .namespace("default")
  .configmaps
  .patchGeneric(
    "test",
    customMergeObject,
    patch = PatchType.Merge
  )
```

## Implementing new requests
you can also implement your own requests easily (for example CRDs or non-standard resources), however if you need a request that is widely used and is standard, please open an issue or better, a pull request, so everyone can use it.

First you may need to define your custom data models (see [here][Custom Resources])  
Then, you can define your APIs requests:

### Specific requests

```scala mdoc
import dev.hnaderi.k8s.client._

case class MyCustomRequest(name: String) extends GetRequest[CustomResource](
  s"/apis/my.custom-resource.io/$name"
)
```

Some of the other available request types are:  
@:api(dev.hnaderi.k8s.client.ListingRequest), 
@:api(dev.hnaderi.k8s.client.GetRequest), 
@:api(dev.hnaderi.k8s.client.CreateRequest), 
@:api(dev.hnaderi.k8s.client.ReplaceRequest), 
@:api(dev.hnaderi.k8s.client.PartialUpdateRequest), 
@:api(dev.hnaderi.k8s.client.DeleteCollectionRequest), 
@:api(dev.hnaderi.k8s.client.DeleteRequest), 
@:api(dev.hnaderi.k8s.client.APIResourceListingRequest), 
@:api(dev.hnaderi.k8s.client.APIGroupListingRequest)

### API group requests

```scala mdoc
object MyCustomAPIGroup
    extends APIGroupAPI("/apis/custom.api.group.type/v1")

object MyCustomResourceAPIs
    extends MyCustomAPIGroup.ClusterResourceAPI[
      CustomResource,
      CustomResourceList
    ]("customresourcedefinitions")
```

Usage:

```scala mdoc:silent
val customRequest = MyCustomResourceAPIs.list()
```
