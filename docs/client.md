# Client

Scala k8s provides a Kubernetes client built on top of a generic HTTP client, allowing different backends based on your project's ecosystem.

```scala mdoc:invisible
import cats.effect._
import dev.hnaderi.k8s._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client.implicits._
import io.circe.Json
import io.k8s.api.core.v1.ConfigMap
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

case class CustomResourceSpec(someKey: String)
case class CustomResource(spec: CustomResourceSpec)
object CustomResource {
  implicit val decoder: dev.hnaderi.k8s.utils.Decoder[CustomResource] =
    new dev.hnaderi.k8s.utils.Decoder[CustomResource] {
      def apply[T: dev.hnaderi.k8s.utils.Reader](t: T): Either[String, CustomResource] = Left("")
    }
  implicit val encoder: dev.hnaderi.k8s.utils.Encoder[CustomResource] =
    new dev.hnaderi.k8s.utils.Encoder[CustomResource] {
      def apply[T: dev.hnaderi.k8s.utils.Builder](o: CustomResource): T =
        dev.hnaderi.k8s.utils.ObjectWriter[T]().build
    }
}
case class CustomResourceList()
object CustomResourceList {
  implicit val decoder: dev.hnaderi.k8s.utils.Decoder[CustomResourceList] =
    new dev.hnaderi.k8s.utils.Decoder[CustomResourceList] {
      def apply[T: dev.hnaderi.k8s.utils.Reader](t: T): Either[String, CustomResourceList] = Left("")
    }
}
```

The following examples use `kubectl proxy` for simplicity.

## Http4s based client

Http4s based client supports all APIs.

```scala mdoc:to-string
import dev.hnaderi.k8s.client.http4s.EmberKubernetesClient
import org.http4s.circe._

val buildClient = EmberKubernetesClient[IO].defaultConfig[Json]

val getNodes = buildClient.use(APIs.nodes.list().send)

val watchNodes = fs2.Stream.resource(buildClient).flatMap(APIs.nodes.list().listen)

val getConfigMaps =
  buildClient.use(client =>
    APIs
      .namespace("kube-system")
      .configMaps
      .get("kube-proxy")
      .send(client)
  )
```

## ZIO based client

```scala
import dev.hnaderi.k8s.client.ZIOKubernetesClient

val client = ZIOKubernetesClient.make("http://localhost:8001")
val nodes = ZIOKubernetesClient.send(APIs.nodes.list())
```

## Sttp based client

```scala mdoc:compile-only
import dev.hnaderi.k8s.client.SttpJdkURLClientBuilder
import sttp.client3.circe._

val client = SttpJdkURLClientBuilder.defaultConfig[Json]

val nodes = APIs.nodes.list().send(client)
nodes.body.items.flatMap(_.metadata).flatMap(_.name).foreach(println)
```

# API calls

## Working with requests

Requests are plain data, so you can manipulate or pass them like any normal value:

```scala mdoc
val sysConfig = APIs
  .namespace("kube-system")
  .configMaps

val defaultConfig = sysConfig.copy(namespace = "default")
```

## Advanced requests

Strategic merge patch:

```scala mdoc:to-string
val patch1 = APIs
  .namespace("default")
  .configMaps
  .patch(
    "test",
    ConfigMap(metadata = ObjectMeta(labels = Map("new" -> "label")))
  )
```

[Json patch](https://www.rfc-editor.org/rfc/rfc6902):

```scala mdoc:to-string
val patch2 = APIs
  .namespace("default")
  .configMaps
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
  .configMaps
  .serverSideApply("test", ConfigMap(), fieldManager = "my-operator")
```

[Json merge patch](https://www.rfc-editor.org/rfc/rfc7386):

```scala mdoc:to-string
val patch4 = APIs
  .namespace("default")
  .configMaps
  .patch(
    "test",
    ConfigMap(metadata = ObjectMeta(labels = Map("new" -> "label"))),
    patch = PatchType.Merge
  )
```

Custom type merge:

```scala mdoc:to-string
type CustomMerge = String
val customMergeObject: CustomMerge = ""

val patch5 = APIs
  .namespace("default")
  .configMaps
  .patchGeneric(
    "test",
    customMergeObject,
    patch = PatchType.Merge
  )
```

## Pod exec

The `http4s-jdk` and `http4s-netty` backends support executing commands inside pods over WebSockets using the Kubernetes exec API.

```scala
libraryDependencies += "dev.hnaderi" %% "scala-k8s-http4s-jdk" % "@VERSION@"
```

```scala mdoc:compile-only
import dev.hnaderi.k8s.client.http4s._
import fs2.Stream

// JDKKubernetesClient also supports defaultConfigWithExec, kubeconfigWithExec,
// fromConfigWithExec, fromWithExec, podConfigWithExec
val execApp: IO[Unit] =
  JDKKubernetesClient[IO].defaultConfigWithExec[Json].use { client =>
    val pipe = client.pipe(
      APIs
        .namespace("default")
        .pods
        .exec("my-pod", Seq("sh", "-c", "echo hello"))
    )

    pipe(Stream.empty)
      .evalMap {
        case ExecEvent.Stdout(data) =>
          IO.println(s"stdout: ${new String(data, "UTF-8").trim}")
        case ExecEvent.Stderr(data) =>
          IO.println(s"stderr: ${new String(data, "UTF-8").trim}")
        case ExecEvent.Error(status) =>
          IO.println(s"exit status: ${status.status.getOrElse("unknown")}")
      }
      .compile
      .drain
  }
```

To pass stdin, stream `ExecInput.Stdin` values into the pipe:

```scala mdoc:compile-only
import dev.hnaderi.k8s.client.http4s._
import fs2.Stream

val stdinApp: IO[Unit] =
  JDKKubernetesClient[IO].defaultConfigWithExec[Json].use { client =>
    val pipe = client.pipe(
      APIs
        .namespace("default")
        .pods
        .exec("my-pod", Seq("cat"), stdinEnabled = true)
    )

    val input: Stream[IO, ExecInput] =
      Stream.emit(ExecInput.Stdin("hello\n".getBytes("UTF-8")))

    pipe(input)
      .collect { case ExecEvent.Stdout(data) => new String(data, "UTF-8") }
      .evalMap(IO.println)
      .compile
      .drain
  }
```

The exec stream terminates automatically when the process exits.

## Implementing new requests

You can implement your own requests for CRDs or non-standard resources.
First define your data models (see [Custom Resources][Custom Resources]), then define the request types:

### Specific requests

```scala mdoc
case class MyCustomRequest(name: String) extends GetRequest[CustomResource](
  s"/apis/my.custom-resource.io/$name"
)
```

Other available request types:
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

```scala mdoc:silent
val customRequest = MyCustomResourceAPIs.list()
```
