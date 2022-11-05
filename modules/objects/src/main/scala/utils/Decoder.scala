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

abstract class Decoder[+R] { self =>
  def apply[T: Reader](t: T): Either[String, R]

  final def map[RR](f: R => RR): Decoder[RR] = new Decoder[RR] {
    def apply[T: Reader](t: T): Either[String, RR] = self(t).map(f)
  }
  final def emap[RR](f: R => Either[String, RR]): Decoder[RR] =
    new Decoder[RR] {
      def apply[T: Reader](t: T): Either[String, RR] = self(t).flatMap(f)
    }
  final def orElse[RR >: R](dec: Decoder[RR]): Decoder[RR] =
    new Decoder[RR] {
      def apply[T: Reader](t: T): Either[String, RR] = {
        self(t) match {
          case Right(value) => Right(value)
          case _            => dec(t)
        }
      }
    }
}

object Decoder {
  def apply[R](implicit d: Decoder[R]): Decoder[R] = d

  implicit lazy val intDecoder: Decoder[Int] =
    new Decoder[Int] {
      def apply[T](t: T)(implicit r: Reader[T]): Either[String, Int] = r.int(t)
    }
  implicit lazy val longDecoder: Decoder[Long] =
    new Decoder[Long] {
      def apply[T](t: T)(implicit r: Reader[T]): Either[String, Long] =
        r.long(t)
    }
  implicit lazy val doubleDecoder: Decoder[Double] =
    new Decoder[Double] {
      def apply[T](t: T)(implicit r: Reader[T]): Either[String, Double] =
        r.double(t)
    }
  implicit lazy val stringDecoder: Decoder[String] =
    new Decoder[String] {
      def apply[T](t: T)(implicit r: Reader[T]): Either[String, String] =
        r.string(t)
    }
  implicit lazy val booleanDecoder: Decoder[Boolean] =
    new Decoder[Boolean] {
      def apply[T](t: T)(implicit r: Reader[T]): Either[String, Boolean] =
        r.bool(t)
    }
  implicit def arrDecoder[A](implicit dec: Decoder[A]): Decoder[Seq[A]] =
    new Decoder[Seq[A]] {
      def apply[T](t: T)(implicit r: Reader[T]): Either[String, Seq[A]] = {
        r.array(t)
          .flatMap(_.foldLeft[Either[String, List[A]]](Right(Nil)) {
            case (el, a) => el.flatMap(l => dec(a).map(l :+ _))
          })
      }
    }

  implicit def mapDecoder[A](implicit
      dec: Decoder[A]
  ): Decoder[Map[String, A]] =
    new Decoder[Map[String, A]] {
      def apply[T](
          t: T
      )(implicit r: Reader[T]): Either[String, Map[String, A]] =
        r.obj(t)
          .flatMap(
            _.foldLeft[Either[String, Map[String, A]]](Right(Map.empty)) {
              case (el, (k, a)) => el.flatMap(l => dec(a).map(l.updated(k, _)))
            }
          )
    }

  def const[R](r: R): Decoder[R] = new Decoder[R] {
    def apply[T: Reader](t: T): Either[String, R] = Right(r)
  }
  def failed[R](msg: String): Decoder[R] = new Decoder[R] {
    def apply[T: Reader](t: T): Either[String, R] = Left(msg)
  }
}
