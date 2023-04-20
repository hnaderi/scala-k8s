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

package dev.hnaderi.k8s.circe

import dev.hnaderi.k8s.utils.Reader
import io.circe.Decoder
import io.circe.Json
import io.circe.JsonObject

private[circe] object CirceReader extends Reader[Json] {
  /* NOTE that this import is required to use correct implicit instances */
  import io.circe.Decoder._

  private def convert[T: Decoder](json: Json): Either[String, T] =
    json.as[T].left.map(_.getMessage())

  override def string(t: Json): Either[String, String] = convert[String](t)

  override def int(t: Json): Either[String, Int] =
    t.asNumber.flatMap(_.toInt) match {
      case Some(v) => Right(v)
      case None    => Left(s"Expected Integer, but got: $t")
    }

  override def long(t: Json): Either[String, Long] =
    t.asNumber.flatMap(_.toLong) match {
      case Some(v) => Right(v)
      case None    => Left(s"Expected Long, but got: $t")
    }

  override def double(t: Json): Either[String, Double] =
    t.asNumber match {
      case Some(v) => Right(v.toDouble)
      case None    => Left(s"Expected Double, but got: $t")
    }

  override def bool(t: Json): Either[String, Boolean] = convert[Boolean](t)

  override def array(t: Json): Either[String, Iterable[Json]] =
    convert[Iterable[Json]](t)

  override def obj(t: Json): Either[String, Iterable[(String, Json)]] =
    convert[JsonObject](t).map(_.toMap)

  override def opt(t: Json): Option[Json] = if (t.isNull) None else Some(t)
}
