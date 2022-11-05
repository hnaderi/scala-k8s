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

import dev.hnaderi.k8s.utils._
import org.typelevel.jawn.Facade

package object jawn {
  implicit def jawnFacade[T](implicit
      builder: Builder[T]
  ): Facade.SimpleFacade[T] =
    new Facade.SimpleFacade[T] {
      override def jnull: T = builder.nil
      override def jfalse: T = builder.of(false)
      override def jtrue: T = builder.of(true)
      override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): T = {
        val n = BigDecimal(s.toString)
        if (n.isValidInt) builder.of(n.toIntExact)
        else if (n.isValidLong) builder.of(n.toLongExact)
        else builder.of(n.toDouble)
      }
      override def jstring(s: CharSequence): T = builder.of(s.toString)
      override def jarray(vs: List[T]): T = builder.arr(vs)
      override def jobject(vs: Map[String, T]): T = builder.obj(vs)
    }
}
