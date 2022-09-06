## scala-k8s

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

### Getting started

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

import java.nio.file.Path
```

Now we can define any kubernetes object

#### ConfigMap example

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
    "blob2" -> Path.of(".scalafmt.conf"),
    "other inline data" -> "some other data"
  )
)
```

#### Deployment example

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

#### Service example

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

#### Manifest example

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
val config2 = config
  .addData("new-key" -> "new value")
  .withImmutable(true)
  .mapMetadata(_.withName("new-config").withNamespace("production"))
```

All fields have the following helper methods:

* `withFieldName` that acts like a setter
* `addFieldName` for lists and maps, adds new values
* `mapFieldName` transforms using a function


```scala mdoc
println(config2.asManifest)
```
