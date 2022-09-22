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

package dev.hnaderi.k8s.playJson

import dev.hnaderi.k8s.KObject
import dev.hnaderi.k8s.scalacheck.Generators.arbitraryKObjects
import dev.hnaderi.k8s.test.CodecSuite
import org.scalacheck.Prop.forAll
import play.api.libs.json._

class PlayJsonSuite extends CodecSuite[JsValue] {
  property("Play json must be reversible") {
    forAll { (obj: KObject) =>
      val json = Json.toJson(obj)
      val dec = json.validate[KObject]
      assertEquals(dec, JsSuccess(obj))
    }
  }
}
