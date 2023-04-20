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

final case class ObjectReader[T](fields: Iterable[(String, T)])(implicit
    reader: Reader[T]
) {
  private lazy val m = fields.toMap
  def get(key: String): Either[String, T] =
    m.get(key).toRight(s"no such field $key exists!")
  def getOpt(key: String): Either[Nothing, Option[T]] =
    Right(m.get(key).map(reader.opt).getOrElse(None))

  def read[A](key: String)(implicit dec: Decoder[A]): Either[String, A] =
    get(key).flatMap(dec(_))
  def readOpt[A](
      key: String
  )(implicit dec: Decoder[A]): Either[String, Option[A]] =
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
