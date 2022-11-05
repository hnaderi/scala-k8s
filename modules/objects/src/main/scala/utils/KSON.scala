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

sealed trait KSON extends Serializable with Product {
  def foldTo[T: Builder]: T
}
object KSON {
  final case class KString(value: String) extends KSON {
    def foldTo[T](implicit b: Builder[T]): T = b.of(value)
  }
  final case class KInt(value: Int) extends KSON {
    def foldTo[T](implicit b: Builder[T]): T = b.of(value)
  }
  final case class KLong(value: Long) extends KSON {
    def foldTo[T](implicit b: Builder[T]): T = b.of(value)
  }
  final case class KDouble(value: Double) extends KSON {
    def foldTo[T](implicit b: Builder[T]): T = b.of(value)
  }
  final case class KBool(value: Boolean) extends KSON {
    def foldTo[T](implicit b: Builder[T]): T = b.of(value)
  }
  final case class KArr(value: Iterable[KSON]) extends KSON {
    def foldTo[T](implicit b: Builder[T]): T = b.arr(value.map(_.foldTo[T]))
  }
  final case class KObj(value: Iterable[(String, KSON)]) extends KSON {
    def foldTo[T](implicit b: Builder[T]): T =
      b.obj(value.map { case (k, v) => (k, v.foldTo[T]) })
  }
  case object KNull extends KSON {
    def foldTo[T](implicit b: Builder[T]): T = b.nil
  }

  implicit val builderInstance: Builder[KSON] = new Builder[KSON] {

    override def of(str: String): KSON = KString(str)

    override def of(i: Int): KSON = KInt(i)

    override def of(l: Long): KSON = KLong(l)

    override def of(l: Double): KSON = KDouble(l)

    override def of(b: Boolean): KSON = KBool(b)

    override def arr(a: Iterable[KSON]): KSON = KArr(a)

    override def obj(values: Iterable[(String, KSON)]): KSON = KObj(values)

    override def nil: KSON = KNull

  }

  implicit val readerInstance: Reader[KSON] = new Reader[KSON] {

    override def string(t: KSON): Either[String, String] = t match {
      case KString(v) => Right(v)
      case _          => Left("Not a string!")
    }

    override def int(t: KSON): Either[String, Int] = t match {
      case KInt(i)                    => Right(i)
      case KLong(l) if l.isValidInt   => Right(l.toInt)
      case KDouble(l) if l.isValidInt => Right(l.toInt)
      case _                          => Left("Not a valid integer!")
    }

    override def long(t: KSON): Either[String, Long] = t match {
      case KLong(l)                => Right(l)
      case KInt(i)                 => Right(i.toLong)
      case KDouble(l) if l.isWhole => Right(l.toLong)
      case _                       => Left("Not a valid long!")
    }

    override def double(t: KSON): Either[String, Double] = t match {
      case KDouble(l) => Right(l)
      case KLong(l)   => Right(l.toDouble)
      case KInt(i)    => Right(i.toDouble)
      case _          => Left("Not a valid double!")
    }

    override def bool(t: KSON): Either[String, Boolean] = t match {
      case KBool(b) => Right(b)
      case _        => Left("Not a boolean value!")
    }

    override def array(t: KSON): Either[String, Iterable[KSON]] = t match {
      case KArr(vs) => Right(vs)
      case _        => Left("Not an array!")
    }

    override def obj(t: KSON): Either[String, Iterable[(String, KSON)]] =
      t match {
        case KObj(fs) => Right(fs)
        case _        => Left("Not an object!")
      }
  }

  implicit val encoder: Encoder[KSON] = new Encoder[KSON] {
    def apply[T: Builder](r: KSON): T = r.foldTo[T]
  }
}
