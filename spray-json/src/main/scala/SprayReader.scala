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

package dev.hnaderi.k8s.sprayJson

import spray.json._
import dev.hnaderi.k8s.utils._

import scala.util.Try

private[sprayJson] object SprayReader extends Reader[JsValue] {

  override def string(t: JsValue): Either[String, String] = t match {
    case JsString(value) => Right(value)
    case _               => Left("Not a string!")
  }

  private def conv[T](fd: => T): Either[String, T] =
    Try(fd).toEither.left.map(_.getMessage())

  override def int(t: JsValue): Either[String, Int] = t match {
    case JsNumber(value) => conv(value.toIntExact)
    case _               => Left("Not an integer!")
  }

  override def long(t: JsValue): Either[String, Long] = t match {
    case JsNumber(value) => conv(value.toLongExact)
    case _               => Left("Not a long!")
  }

  override def double(t: JsValue): Either[String, Double] = t match {
    case JsNumber(value) => conv(value.toDouble)
    case _               => Left("Not a double!")
  }

  override def bool(t: JsValue): Either[String, Boolean] = t match {
    case JsBoolean(v) => Right(v)
    case _            => Left("Not a boolean value!")
  }

  override def array(t: JsValue): Either[String, Iterable[JsValue]] = t match {
    case JsArray(values) => Right(values)
    case _               => Left("Not an array!")
  }

  override def obj(t: JsValue): Either[String, Iterable[(String, JsValue)]] =
    t match {
      case JsObject(fields) => Right(fields)
      case _                => Left("Not an object!")
    }

}
