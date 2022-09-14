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

package dev.hnaderi.k8s.utils

trait Encoder[R, T] extends Serializable {
  def apply(r: R): T
}

object Encoder {
  def apply[R, T](implicit enc: Encoder[R, T]): Encoder[R, T] = enc
  implicit def intBuilder[T](implicit b: Builder[T]): Encoder[Int, T] =
    new Encoder[Int, T] {
      def apply(r: Int): T =
        b.of(r)
    }
  implicit def longBuilder[T](implicit b: Builder[T]): Encoder[Long, T] =
    new Encoder[Long, T] {
      def apply(r: Long): T =
        b.of(r)
    }
  implicit def doubleBuilder[T](implicit b: Builder[T]): Encoder[Double, T] =
    new Encoder[Double, T] {
      def apply(r: Double): T =
        b.of(r)
    }

  implicit def stringBuilder[T](implicit b: Builder[T]): Encoder[String, T] =
    new Encoder[String, T] {
      def apply(r: String): T =
        b.of(r)
    }

  implicit def booleanBuilder[T](implicit b: Builder[T]): Encoder[Boolean, T] =
    new Encoder[Boolean, T] {
      def apply(r: Boolean): T =
        b.of(r)
    }

  implicit def seqBuilder[A, T](implicit
      b: Builder[T],
      enc: Encoder[A, T]
  ): Encoder[Seq[A], T] =
    new Encoder[Seq[A], T] {
      def apply(r: Seq[A]): T = b.arr(r.map(enc(_)))
    }

  implicit def mapBuilder[A, T](implicit
      b: Builder[T],
      enc: Encoder[A, T]
  ): Encoder[Map[String, A], T] =
    new Encoder[Map[String, A], T] {
      def apply(r: Map[String, A]): T = b.obj(r.map { case (k, v) =>
        (k, enc(v))
      })
    }

  def emptyObj[R, T: Builder]: Encoder[R, T] = new Encoder[R, T] {
    def apply(r: R): T = Builder[T].ofFields()
  }
}
