package dev.hnaderi.k8s.utils

final case class ObjectReader[T](fields: Iterable[(String, T)])(implicit
    reader: Reader[T]
) {
  private lazy val m = fields.toMap
  def get(key: String): Either[String, T] =
    m.get(key).toRight(s"no such field $key exists!")
  def getOpt(key: String): Either[Nothing, Option[T]] =
    Right(m.get(key).map(Some(_)).getOrElse(None))

  def read[A](key: String)(implicit dec: Decoder[T, A]): Either[String, A] =
    get(key).flatMap(dec(_))
  def readOpt[A](
      key: String
  )(implicit dec: Decoder[T, A]): Either[String, Option[A]] =
    getOpt(key).flatMap {
      case Some(value) => dec(value).map(Some(_))
      case None        => Right(None)
    }

  def getInt(key: String): Either[String, Int] = get(key).flatMap(reader.int)
  def getLong(key: String): Either[String, Long] = get(key).flatMap(reader.long)
  def getString(key: String): Either[String, String] =
    get(key).flatMap(reader.string)
  def getBool(key: String): Either[String, Boolean] =
    get(key).flatMap(reader.bool)
  def getOptObj[T2](key: String)(
      f: T => Either[String, T2]
  ): Either[String, Option[T2]] =
    m.get(key) match {
      case None        => Right(None)
      case Some(value) => f(value).map(Some(_))
    }
}

object ObjectReader {
  def apply[T](t: T)(implicit
      reader: Reader[T]
  ): Either[String, ObjectReader[T]] = reader.obj(t).map(new ObjectReader(_))
}
