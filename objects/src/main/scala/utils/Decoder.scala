package dev.hnaderi.k8s.utils

abstract class Decoder[T, +R] { self =>
  def apply(t: T): Either[String, R]
  final def map[RR](f: R => RR): Decoder[T, RR] = new Decoder[T, RR] {
    def apply(t: T): Either[String, RR] = self(t).map(f)
  }
  final def emap[RR](f: R => Either[String, RR]): Decoder[T, RR] =
    new Decoder[T, RR] {
      def apply(t: T): Either[String, RR] = self(t).flatMap(f)
    }
  final def orElse[RR >: R](dec: Decoder[T, RR]): Decoder[T, RR] =
    new Decoder[T, RR] {
      def apply(t: T): Either[String, RR] = {
        self(t) match {
          case Right(value) => Right(value)
          case other        => dec(t)
        }
      }
    }
}

object Decoder {
  def apply[T, R](implicit d: Decoder[T, R]): Decoder[T, R] = d

  implicit def intDecoder[T](implicit r: Reader[T]): Decoder[T, Int] =
    new Decoder[T, Int] {
      def apply(t: T): Either[String, Int] = r.int(t)
    }
  implicit def longDecoder[T](implicit r: Reader[T]): Decoder[T, Long] =
    new Decoder[T, Long] {
      def apply(t: T): Either[String, Long] = r.long(t)
    }
  implicit def doubleDecoder[T](implicit r: Reader[T]): Decoder[T, Double] =
    new Decoder[T, Double] {
      def apply(t: T): Either[String, Double] = r.double(t)
    }
  implicit def stringDecoder[T](implicit r: Reader[T]): Decoder[T, String] =
    new Decoder[T, String] {
      def apply(t: T): Either[String, String] = r.string(t)
    }
  implicit def booleanDecoder[T](implicit r: Reader[T]): Decoder[T, Boolean] =
    new Decoder[T, Boolean] {
      def apply(t: T): Either[String, Boolean] = r.bool(t)
    }
  implicit def arrDecoder[T, A](implicit
      r: Reader[T],
      dec: Decoder[T, A]
  ): Decoder[T, Seq[A]] =
    new Decoder[T, Seq[A]] {
      def apply(t: T): Either[String, Seq[A]] = {
        r.array(t)
          .flatMap(_.foldLeft[Either[String, List[A]]](Right(Nil)) {
            case (el, a) => el.flatMap(l => dec(a).map(l :+ _))
          })
      }
    }

  implicit def mapDecoder[T, A](implicit
      r: Reader[T],
      dec: Decoder[T, A]
  ): Decoder[T, Map[String, A]] =
    new Decoder[T, Map[String, A]] {
      def apply(t: T): Either[String, Map[String, A]] =
        r.obj(t)
          .flatMap(
            _.foldLeft[Either[String, List[(String, A)]]](Right(Nil)) {
              case (el, (k, a)) => el.flatMap(l => dec(a).map((k, _) :: l))
            }.map(_.toMap)
          )
    }

  def const[T, R](r: R): Decoder[T, R] = new Decoder[T, R] {
    def apply(t: T): Either[String, R] = Right(r)
  }
}
