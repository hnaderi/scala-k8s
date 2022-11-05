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

trait Encoder[R] extends Serializable { self =>
  def apply[T: Builder](r: R): T
  final def contramap[A](f: A => R): Encoder[A] = new Encoder[A] {
    def apply[T: Builder](a: A): T = self(f(a))
  }
}

object Encoder {
  def apply[R](implicit enc: Encoder[R]): Encoder[R] = enc
  implicit lazy val intBuilder: Encoder[Int] =
    new Encoder[Int] {
      def apply[T](r: Int)(implicit b: Builder[T]): T = b.of(r)
    }

  implicit lazy val longBuilder: Encoder[Long] =
    new Encoder[Long] {
      def apply[T](r: Long)(implicit b: Builder[T]): T =
        b.of(r)
    }
  implicit lazy val doubleBuilder: Encoder[Double] =
    new Encoder[Double] {
      def apply[T](r: Double)(implicit b: Builder[T]): T =
        b.of(r)
    }

  implicit lazy val stringBuilder: Encoder[String] =
    new Encoder[String] {
      def apply[T](r: String)(implicit b: Builder[T]): T =
        b.of(r)
    }

  implicit lazy val booleanBuilder: Encoder[Boolean] =
    new Encoder[Boolean] {
      def apply[T](r: Boolean)(implicit b: Builder[T]): T =
        b.of(r)
    }

  implicit def seqBuilder[A](implicit enc: Encoder[A]): Encoder[Seq[A]] =
    new Encoder[Seq[A]] {
      def apply[T](r: Seq[A])(implicit b: Builder[T]): T = b.arr(r.map(enc(_)))
    }

  implicit def listBuilder[A](implicit enc: Encoder[A]): Encoder[List[A]] =
    seqBuilder[A].contramap(_.toList)

  implicit def mapBuilder[A](implicit
      enc: Encoder[A]
  ): Encoder[Map[String, A]] =
    new Encoder[Map[String, A]] {
      def apply[T](r: Map[String, A])(implicit b: Builder[T]): T = b.obj(r.map {
        case (k, v) =>
          (k, enc(v))
      })
    }

  def emptyObj[R]: Encoder[R] = new Encoder[R] {
    def apply[T: Builder](r: R): T = Builder[T].ofFields()
  }
}
