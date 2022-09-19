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

package io.k8s.apimachinery.pkg.util.intstr

import dev.hnaderi.k8s.utils._

/** IntOrString is a type that can hold an int32 or a string. When used in JSON
  * or YAML marshalling and unmarshalling, it produces or consumes the inner
  * type. This allows you to have, for example, a JSON field that can accept a
  * name or number.
  */
trait IntOrString extends Any
object IntOrString {
  final case class IntValue(value: Int) extends AnyVal with IntOrString
  final case class StringValue(value: String) extends AnyVal with IntOrString
  def apply(int: Int): IntValue = IntValue(int)
  def apply(str: String): StringValue = StringValue(str)

  implicit val encoder: Encoder[IntOrString] = new Encoder[IntOrString] {
    def apply[T](r: IntOrString)(implicit
        builder: Builder[T]
    ): T = r match {
      case IntValue(i)    => builder.of(i)
      case StringValue(s) => builder.of(s)
    }
  }
  implicit val decoder: Decoder[IntOrString] = Decoder[Int]
    .map(IntValue(_))
    .orElse(Decoder[String].map(StringValue(_)))
}
