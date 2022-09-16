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
package test

import dev.hnaderi.k8s.scalacheck.Generators._
import dev.hnaderi.k8s.utils.Builder
import dev.hnaderi.k8s.utils.Reader
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

abstract class CodecSuite[T: Builder: Reader] extends ScalaCheckSuite {
  property("Codec must be reversible") {
    forAll { (o: KObject) =>
      val encoded = o.foldTo[T]
      val decoded = KObject.decoder[T].apply(encoded)
      assertEquals(decoded, Right(o))
    }
  }
}
