# Writing an Operator

A Kubernetes operator watches for changes to custom (or built-in) resources and reconciles the cluster state in response.
The basic loop is: watch, reconcile, update status.

## Dependencies

An operator needs a backend that supports streaming watch events.
The http4s-ember backend works on JVM, JS, and Native:

```scala
libraryDependencies ++= Seq(
  "dev.hnaderi" %% "scala-k8s-http4s-ember" % "@VERSION@",
  "dev.hnaderi" %% "scala-k8s-circe"        % "@VERSION@"
)
```

```scala mdoc:invisible
import cats.effect._
import dev.hnaderi.k8s.utils._
import dev.hnaderi.k8s.client._
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.circe._
import dev.hnaderi.k8s.client.http4s.EmberKubernetesClient
import io.circe.Json
import io.k8s.api.apps.v1.Deployment
import io.k8s.api.apps.v1.DeploymentSpec
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.PodSpec
import io.k8s.api.core.v1.PodTemplateSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.LabelSelector
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.circe._
```

## Connecting to the API server

`defaultConfig` tries the kubeconfig file first (`KUBECONFIG` env var, then `~/.kube/config`),
and falls back to the pod's service account automatically if no kubeconfig is found.
This means the same code works both during local development and when deployed inside a pod:

```scala mdoc:compile-only
val client = EmberKubernetesClient[IO].defaultConfig[Json]
```

To connect explicitly using the pod's service account
(from `/var/run/secrets/kubernetes.io/serviceaccount`), use `podConfig` directly:

```scala mdoc:compile-only
val podClient = EmberKubernetesClient[IO].podConfig[Json]
```

## 1. Model your CRD

Define Scala case classes for your custom resource and implement `Encoder` / `Decoder` instances.
A list wrapper type is also needed; Kubernetes always returns a typed list when you list resources.
See [Custom Resources][Custom Resources] for the full codec pattern.

```scala mdoc:silent
case class MyAppSpec(replicas: Int, image: String)
case class MyAppStatus(ready: Boolean)
case class MyApp(spec: MyAppSpec, status: Option[MyAppStatus] = None)
case class MyAppList(items: Seq[MyApp])

object MyAppSpec {
  implicit val encoder: Encoder[MyAppSpec] = new Encoder[MyAppSpec] {
    def apply[T: Builder](o: MyAppSpec): T =
      ObjectWriter[T]()
        .write("replicas", o.replicas)
        .write("image", o.image)
        .build
  }
  implicit val decoder: Decoder[MyAppSpec] = new Decoder[MyAppSpec] {
    def apply[T: Reader](t: T): Either[String, MyAppSpec] = for {
      obj      <- ObjectReader(t)
      replicas <- obj.read[Int]("replicas")
      image    <- obj.read[String]("image")
    } yield MyAppSpec(replicas, image)
  }
}

object MyAppStatus {
  implicit val encoder: Encoder[MyAppStatus] = new Encoder[MyAppStatus] {
    def apply[T: Builder](o: MyAppStatus): T =
      ObjectWriter[T]().write("ready", o.ready).build
  }
  implicit val decoder: Decoder[MyAppStatus] = new Decoder[MyAppStatus] {
    def apply[T: Reader](t: T): Either[String, MyAppStatus] = for {
      obj   <- ObjectReader(t)
      ready <- obj.read[Boolean]("ready")
    } yield MyAppStatus(ready)
  }
}

object MyApp {
  implicit val encoder: Encoder[MyApp] = new Encoder[MyApp] {
    def apply[T: Builder](o: MyApp): T =
      ObjectWriter[T]()
        .write("apiVersion", "example.com/v1")
        .write("kind", "MyApp")
        .write("spec", o.spec)
        .build
  }
  implicit val decoder: Decoder[MyApp] = new Decoder[MyApp] {
    def apply[T: Reader](t: T): Either[String, MyApp] = for {
      obj    <- ObjectReader(t)
      spec   <- obj.read[MyAppSpec]("spec")
      status <- obj.readOpt[MyAppStatus]("status")
    } yield MyApp(spec, status)
  }
}

object MyAppList {
  implicit val decoder: Decoder[MyAppList] = new Decoder[MyAppList] {
    def apply[T: Reader](t: T): Either[String, MyAppList] = for {
      obj   <- ObjectReader(t)
      items <- obj.read[Seq[MyApp]]("items")
    } yield MyAppList(items)
  }
}
```

## 2. Register API group requests

Extend `APIGroupAPI` with the appropriate resource API class to get a full set of typed CRUD and watch request builders.
Use `NamespacedResourceAPI` for namespace-scoped CRDs or `ClusterResourceAPI` for cluster-scoped ones.

```scala mdoc:silent
object MyAppsAPIGroup extends APIGroupAPI("/apis/example.com/v1")

object MyAppsAPI
    extends MyAppsAPIGroup.NamespacedResourceAPI[MyApp, MyAppList]("myapps")
```

## 3. The watch loop

`list().listen(client)` opens a long-running watch stream.
Each element is a `WatchEvent[MyApp]` with an `event` field of type `WatchEventType`:
`ADDED`, `MODIFIED`, `DELETED`, `BOOKMARK`, or `ERROR`.

```scala mdoc:compile-only
def reconcile(event: WatchEvent[MyApp]): IO[Unit] =
  event.event match {
    case WatchEventType.ADDED | WatchEventType.MODIFIED =>
      IO.println(s"Reconciling ${event.payload.spec.image}")
    case WatchEventType.DELETED =>
      IO.println("Resource deleted")
    case WatchEventType.BOOKMARK =>
      IO.unit
    case WatchEventType.ERROR =>
      IO.raiseError(new RuntimeException("watch error"))
    case _ =>
      IO.unit
  }

val operator: IO[Unit] =
  EmberKubernetesClient[IO].defaultConfig[Json].use { client =>
    MyAppsAPI
      .ListInNamespace("default")
      .listen(client)
      .evalMap(reconcile)
      .compile
      .drain
  }
```

## 4. Reconciling child resources with server-side apply

Server-side apply is the recommended way to create or update child resources owned by an operator.
It is idempotent, avoids `resourceVersion` conflicts, and tracks field ownership:

```scala mdoc:compile-only
val fieldManager = "my-operator"

def applyDeployment(
    client: HttpClient[IO],
    namespace: String,
    name: String,
    image: String
): IO[Deployment] =
  APIs
    .namespace(namespace)
    .deployments
    .serverSideApply(
      name,
      Deployment(
        metadata = ObjectMeta(name = name, namespace = namespace),
        spec = DeploymentSpec(
          selector = LabelSelector(matchLabels = Map("app" -> name)),
          template = PodTemplateSpec(
            metadata = ObjectMeta(labels = Map("app" -> name)),
            spec = PodSpec(
              containers = Seq(Container(name = "app", image = image))
            )
          )
        )
      ),
      fieldManager = fieldManager
    )
    .send(client)
```

## 5. Updating status

Write back status via the `/status` sub-resource so the update does not conflict with
spec changes made by users or other controllers.
`ReplaceStatus` targets the `/status` sub-resource automatically:

```scala mdoc:compile-only
def markReady(
    client: HttpClient[IO],
    namespace: String,
    name: String,
    current: MyApp
): IO[MyApp] =
  MyAppsAPI
    .ReplaceStatus(namespace, name, current.copy(status = Some(MyAppStatus(ready = true))))
    .send(client)
```

## Notes

**Leader election:** Run only one active replica to avoid split-brain reconciliation.
Use a Kubernetes `Lease` object in `coordination.k8s.io/v1` as a distributed lock.

**resourceVersion:** When using replace-style updates, read the current object first to carry
the correct `resourceVersion`. Server-side apply handles this automatically.

**Watch reconnect:** The API server closes watch streams after a timeout.
Reconnect automatically by recursing on error:

```scala
import scala.concurrent.duration._

def watchForever(client: KClient[IO]): fs2.Stream[IO, WatchEvent[MyApp]] =
  MyAppsAPI
    .ListInNamespace("default")
    .listen(client)
    .handleErrorWith(_ =>
      fs2.Stream.eval(IO.sleep(1.second)) >> watchForever(client)
    )
```

**RBAC:** The operator's `ServiceAccount` needs `get`, `list`, and `watch` on your CRD,
plus whatever verbs are required to manage child resources:

```yaml
rules:
  - apiGroups: ["example.com"]
    resources: ["myapps", "myapps/status"]
    verbs: ["get", "list", "watch", "update", "patch"]
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "create", "update", "patch", "delete"]
```
