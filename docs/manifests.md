# Manifest and Object Generation

First, import the following:

```scala mdoc
import dev.hnaderi.k8s._
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.manifest._
```

Every other object definition is under the Kubernetes packages `io.k8s` as specified in the spec;
rely on IDE auto-import for those.

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

Merge all resource definitions into one manifest:

```scala mdoc:silent
val all : Seq[KObject] = Seq(service, config, deployment)
val manifest = all.asManifest
```

```scala mdoc
println(manifest)
```

## Helpers

Use helpers to manipulate data models without reconstructing them:

```scala mdoc:silent
val config3 = config
  .addData("new-key" -> "new value")
  .withImmutable(true)
  .mapMetadata(_.withName("new-config").withNamespace("production"))
```

All fields have the following helper methods:

* `withFieldName` acts like a setter
* `addFieldName` adds new values to lists and maps
* `mapFieldName` transforms using a function

```scala mdoc
println(config3.asManifest)
```

## Custom resources

For defining custom resources such as CRDs or non-standard Kubernetes resources:

1. Model your resources:

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

2. Define instances for @:api(dev.hnaderi.k8s.utils.Encoder) and @:api(dev.hnaderi.k8s.utils.Decoder) so your data model becomes serializable:

```scala mdoc
import dev.hnaderi.k8s.utils.*
// in the companion objects
object CustomResourceSpec {
  implicit val decoder: Decoder[CustomResourceSpec] = new Decoder[CustomResourceSpec] {
    def apply[T : Reader](t: T): Either[String, CustomResourceSpec] = for {
      obj   <- ObjectReader(t)
      value <- obj.read[String]("someKey")
      // other fields
    } yield CustomResourceSpec(value)
  }

  implicit val encoder: Encoder[CustomResourceSpec] = new Encoder[CustomResourceSpec] {
    def apply[T : Builder](o: CustomResourceSpec): T = {
      val obj = ObjectWriter[T]()
      obj
        .write("someKey", o.someKey)
        // other fields
        .build
    }
  }
}

object CustomResource {
  implicit val encoder: Encoder[CustomResource] = new Encoder[CustomResource] {
    def apply[T : Builder](o: CustomResource): T = {
      val obj = ObjectWriter[T]()
      obj
        .write("kind", "MyCustomResource")
        .write("apiVersion", "example.com/v1")
        .write("spec", o.spec)
        // other fields
        .build
    }
  }

  implicit val decoder: Decoder[CustomResource] = new Decoder[CustomResource] {
    def apply[T : Reader](t: T): Either[String, CustomResource] = for {
      obj  <- ObjectReader(t)
      spec <- obj.read[CustomResourceSpec]("spec")
      // other fields
    } yield CustomResource(spec)
  }
}
```

```scala mdoc:invisible
case class CustomResourceList()
object CustomResourceList {
  implicit val decoder: Decoder[CustomResourceList] = new Decoder[CustomResourceList] {
    def apply[T : Reader](t: T): Either[String, CustomResourceList] = Left("")
  }
}
```
