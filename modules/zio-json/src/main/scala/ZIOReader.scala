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

package dev.hnaderi.k8s.zioJson

import dev.hnaderi.k8s.utils._
import zio.json.JsonDecoder
import zio.json.ast.Json
import zio.json.ast.Json.Num

private[zioJson] object ZIOReader extends Reader[Json] {

  override def string(t: Json): Either[String, String] =
    t.as(JsonDecoder.string)

  override def int(t: Json): Either[String, Int] = t match {
    case Num(value) if BigDecimal(value).isValidInt =>
      Right(value.intValueExact)
    case other => Left(s"Expected int, but got: $other")
  }

  override def long(t: Json): Either[String, Long] = t match {
    case Num(value) if BigDecimal(value).isValidLong =>
      Right(value.longValueExact())
    case other => Left(s"Expected long, but got: $other")
  }

  override def double(t: Json): Either[String, Double] = t match {
    case Num(value) => Right(value.doubleValue())
    case other      => Left(s"Expected double, but got: $other")
  }

  override def bool(t: Json): Either[String, Boolean] =
    t.as(JsonDecoder.boolean)

  override def array(t: Json): Either[String, Iterable[Json]] =
    t.as(JsonDecoder.list[Json])

  override def obj(t: Json): Either[String, Iterable[(String, Json)]] =
    t.as(JsonDecoder.keyValueChunk[String, Json])

  override def opt(t: Json): Option[Json] = t match {
    case Json.Null => None
    case other     => Some(other)
  }

}
