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

package dev.hnaderi.k8s.json4s

import dev.hnaderi.k8s.utils.Reader
import org.json4s._

private[json4s] object Json4sReader extends Reader[JValue] {

  override def string(t: JValue): Either[String, String] = t match {
    case JString(s) => Right(s)
    case _          => Left("Not a string!")
  }

  override def int(t: JValue): Either[String, Int] = t match {
    case JInt(i) if i.isValidInt  => Right(i.toInt)
    case JLong(l) if l.isValidInt => Right(l.toInt)
    case _                        => Left("Not a valid integer!")
  }

  override def long(t: JValue): Either[String, Long] = t match {
    case JLong(l)                 => Right(l)
    case JInt(i) if i.isValidLong => Right(i.toLong)
    case _                        => Left("Not a valid long!")
  }

  override def double(t: JValue): Either[String, Double] = t match {
    case JDouble(d)                     => Right(d)
    case JDecimal(d) if d.isExactDouble => Right(d.toDouble)
    case JLong(l)                       => Right(l.toDouble)
    case JInt(i) if i.isValidDouble     => Right(i.toDouble)
    case _                              => Left("Not a valid double!")
  }

  override def bool(t: JValue): Either[String, Boolean] = t match {
    case JBool(b) => Right(b)
    case _        => Left("Not a boolean value!")
  }

  override def array(t: JValue): Either[String, Iterable[JValue]] = t match {
    case JArray(values) => Right(values)
    case _              => Left("Not an array!")
  }

  override def obj(t: JValue): Either[String, Iterable[(String, JValue)]] =
    t match {
      case JObject(fields) => Right(fields)
      case _               => Left("Not an object!")
    }

}
