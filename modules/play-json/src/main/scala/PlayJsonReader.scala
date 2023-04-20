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

package dev.hnaderi.k8s.playJson

import dev.hnaderi.k8s.utils.Reader
import play.api.libs.json._

import scala.collection.Seq

private[playJson] object PlayJsonReader extends Reader[JsValue] {
  /* NOTE that this import is required to use correct implicit instances */
  import play.api.libs.json.Reads._

  private def errorMsg(
      err: Seq[(JsPath, Seq[JsonValidationError])]
  ): Either[String, Nothing] = Left {
    val details = err
      .map { case (path, errs) =>
        s"path: ${path.toJsonString}\nerrors: ${errs.map(_.toString()).mkString("\n")}"
      }
      .mkString("\n")
    s"Error while reading! details: $details"
  }

  private def read[T: Reads](t: JsValue): Either[String, T] =
    t.validate[T].fold(errorMsg, Right(_))

  override def string(t: JsValue): Either[String, String] = read[String](t)

  override def int(t: JsValue): Either[String, Int] = read[Int](t)

  override def long(t: JsValue): Either[String, Long] = read[Long](t)

  override def double(t: JsValue): Either[String, Double] = read[Double](t)

  override def bool(t: JsValue): Either[String, Boolean] = read[Boolean](t)

  override def array(t: JsValue): Either[String, Iterable[JsValue]] =
    read[Iterable[JsValue]](t)

  override def obj(t: JsValue): Either[String, Iterable[(String, JsValue)]] =
    read[Map[String, JsValue]](t)

  override def opt(t: JsValue): Option[JsValue] = t match {
    case JsNull => None
    case other  => Some(other)
  }

}
