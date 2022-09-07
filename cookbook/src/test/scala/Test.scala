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

package dev.hnaderi.k8s
package cookbook

import io.k8s.api.apps.v1.Deployment
import io.k8s.apimachinery.pkg.apis.meta.v1.LabelSelector
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import munit.Location

import implicits._

class Test extends FunSuite {
  test("Simple Deployment") {
    val defs = MicroserviceDefinition(
      name = "example",
      version = "0.0.1",
      image = "example:latest"
    )
    val manifest = defs.build

    assertEquals(manifest.size, 1)
    assert(manifest.head.isInstanceOf[Deployment])
    val deployment = manifest.head.asInstanceOf[Deployment]
    assertCommonMeta(deployment.metadata, "example", "0.0.1")
    val spec = assertExists(deployment.spec)
    import spec.template
    assertCommonMeta(template.metadata, "example", "0.0.1")
    assertEquals(
      spec.selector,
      LabelSelector(matchLabels = Map(Labels.name("example")))
    )
    val podSpec = assertExists(template.spec)
    assertEquals(podSpec.containers.size, 1)
    val container = assertExists(podSpec.containers.headOption)
    assertEquals(container.image, Some(defs.image))
    assertEquals(container.name, defs.name)
    assertEquals(container.ports, Some(Nil))
    assertEquals(container.env, Some(Nil))
    assertEquals(container.volumeMounts, Some(Nil))
  }

  private def assertExists[T](opt: Option[T])(implicit loc: Location) = {
    assert(opt.isDefined)
    opt.get
  }

  private def assertCommonMeta(
      meta: Option[ObjectMeta],
      name: String,
      version: String
  )(implicit loc: Location) = {
    val m = assertExists(meta)
    val labels = assertExists(m.labels).toSeq
    assert(labels.contains(Labels.name(name)))
    assert(labels.contains(Labels.version(version)))
  }
}
