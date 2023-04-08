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

package dev.hnaderi.k8s.json4s

import dev.hnaderi.k8s.KObject
import dev.hnaderi.k8s.scalacheck.Generators.arbitraryKObjects
import dev.hnaderi.k8s.test.CodecSuite
import org.json4s._
import org.scalacheck.Prop.forAll

class Json4sSuite extends CodecSuite[JValue] {
  property("Json4s json must be reversible") {
    forAll { (obj: KObject) =>
      val json = Writer[KObject].write(obj)
      val dec = Reader[KObject].readEither(json)
      assertEquals(dec, Right(obj))
    }
  }
}
