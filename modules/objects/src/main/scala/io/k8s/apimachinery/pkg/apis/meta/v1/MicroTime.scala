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

package io.k8s.apimachinery.pkg.apis.meta.v1

import dev.hnaderi.k8s.utils._

/** MicroTime is version of Time with microsecond level precision. */
final case class MicroTime(value: String) extends AnyVal
//TODO

object MicroTime {
  implicit val encoder: Encoder[MicroTime] = new Encoder[MicroTime] {
    def apply[T](r: MicroTime)(implicit
        builder: Builder[T]
    ): T = builder.of(r.value)
  }

  implicit val decoder: Decoder[MicroTime] =
    Decoder[String].map(MicroTime(_))
}
