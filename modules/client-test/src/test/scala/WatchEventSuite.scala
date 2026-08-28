/*
 * Copyright 2022 Hossein Naderi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.hnaderi.k8s.client

import dev.hnaderi.k8s.utils.KSON._
import dev.hnaderi.k8s.utils._
import io.k8s.api.apps.v1.Deployment
import io.k8s.api.core.v1.ConfigMap
import io.k8s.apimachinery.pkg.apis.meta.v1.ListMeta
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import munit.FunSuite

class WatchEventSuite extends FunSuite {
  private def obj(fields: (String, KSON)*): KSON = KObj(fields.toList)
  private def event(tpe: String, o: KSON): KSON =
    obj("type" -> KString(tpe), "object" -> o)

  private def decode[A: Decoder](k: KSON): Either[String, WatchEvent[A]] =
    k.decodeTo[WatchEvent[A]]

  /** What kubernetes actually sends as a bookmark for a deployment watch: an
    * empty deployment, whose required fields are serialized as nulls.
    */
  private val deploymentBookmark = event(
    "BOOKMARK",
    obj(
      "kind" -> KString("Deployment"),
      "apiVersion" -> KString("apps/v1"),
      "metadata" -> obj(
        "resourceVersion" -> KString("650"),
        "creationTimestamp" -> KNull
      ),
      "spec" -> obj(
        "selector" -> KNull,
        "template" -> obj(
          "metadata" -> obj("creationTimestamp" -> KNull),
          "spec" -> obj("containers" -> KNull)
        ),
        "strategy" -> obj()
      ),
      "status" -> obj()
    )
  )

  /** A bookmark for a custom resource is unstructured, and carries nothing but
    * the resource version.
    */
  private val customResourceBookmark = event(
    "BOOKMARK",
    obj(
      "kind" -> KString("MyApp"),
      "apiVersion" -> KString("example.com/v1"),
      "metadata" -> obj("resourceVersion" -> KString("650"))
    )
  )

  private val expiredError = event(
    "ERROR",
    obj(
      "kind" -> KString("Status"),
      "apiVersion" -> KString("v1"),
      "metadata" -> obj(),
      "status" -> KString("Failure"),
      "message" -> KString("too old resource version: 1 (2)"),
      "reason" -> KString("Expired"),
      "code" -> KInt(410)
    )
  )

  test("bookmark of a resource with required fields") {
    assertEquals(
      decode[Deployment](deploymentBookmark),
      Right(WatchEvent.Bookmark(ObjectMeta(resourceVersion = Some("650"))))
    )
  }

  test("bookmark of a custom resource") {
    assertEquals(
      decode[Deployment](customResourceBookmark).map {
        case b: WatchEvent.Bookmark => b.resourceVersion
        case other                  => fail(s"unexpected event: $other")
      },
      Right(Some("650"))
    )
  }

  test("bookmark without metadata") {
    assertEquals(
      decode[Deployment](event("BOOKMARK", obj())),
      Right(WatchEvent.Bookmark(ObjectMeta()))
    )
  }

  test("error carries a status rather than the watched resource") {
    assertEquals(
      decode[Deployment](expiredError),
      Right(
        WatchEvent.Error(
          Status(
            metadata = Some(ListMeta()),
            status = Some("Failure"),
            message = Some("too old resource version: 1 (2)"),
            reason = Some("Expired"),
            code = Some(410)
          )
        )
      )
    )
  }

  test("added, modified and deleted carry the resource") {
    val payload = obj(
      "kind" -> KString("ConfigMap"),
      "apiVersion" -> KString("v1"),
      "metadata" -> obj("name" -> KString("cm")),
      "data" -> obj("key" -> KString("value"))
    )
    val expected = ConfigMap(
      metadata = Some(ObjectMeta(name = Some("cm"))),
      data = Some(Map("key" -> "value"))
    )

    assertEquals(
      decode[ConfigMap](event("ADDED", payload)),
      Right(WatchEvent.Added(expected))
    )
    assertEquals(
      decode[ConfigMap](event("MODIFIED", payload)),
      Right(WatchEvent.Modified(expected))
    )
    assertEquals(
      decode[ConfigMap](event("DELETED", payload)),
      Right(WatchEvent.Deleted(expected))
    )
  }

  test("unknown event types do not touch the object") {
    assertEquals(
      decode[Deployment](event("SOMETHING", KNull)),
      Right(WatchEvent.Other(WatchEventType.Unknown("SOMETHING")))
    )
  }

  test("a payload that does not match the resource still fails") {
    assert(clue(decode[Deployment](event("ADDED", obj()))).isLeft)
  }

  test("round trip") {
    val events: List[WatchEvent[ConfigMap]] = List(
      WatchEvent.Added(
        ConfigMap(metadata = Some(ObjectMeta(name = Some("a"))))
      ),
      WatchEvent.Modified(ConfigMap(data = Some(Map("k" -> "v")))),
      WatchEvent.Deleted(ConfigMap()),
      WatchEvent.Bookmark(ObjectMeta(resourceVersion = Some("650"))),
      WatchEvent.Error(Status(code = Some(410), reason = Some("Expired"))),
      WatchEvent.Other(WatchEventType.Unknown("SOMETHING"))
    )

    events.foreach { e =>
      assertEquals(e.encodeTo[KSON].decodeTo[WatchEvent[ConfigMap]], Right(e))
    }
  }
}
