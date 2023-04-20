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

package dev.hnaderi.k8s

import dev.hnaderi.k8s.utils.Builder
import dev.hnaderi.k8s.utils.Reader
import dev.hnaderi.yaml4s.YAML
import dev.hnaderi.yaml4s.YAML.YNull

private object KObjectYAMLBuilder extends Builder[YAML] {
  def arr(a: Iterable[YAML]): YAML = YAML.arr(a.toVector)
  def nil: YAML = YAML.YNull
  def obj(values: Iterable[(String, YAML)]): YAML = YAML.obj(values.toSeq: _*)
  def of(b: Boolean): YAML = YAML.bool(b)
  def of(l: Double): YAML = YAML.number(l)
  def of(l: Long): YAML = YAML.number(l)
  def of(i: Int): YAML = YAML.number(i.toLong)
  def of(str: String): YAML = YAML.str(str)
}

private object KObjectYAMLReader extends Reader[YAML] {
  override def string(t: YAML): Either[String, String] =
    t.asString.toRight(s"Not a string value: $t")
  override def int(t: YAML): Either[String, Int] =
    t.asNumber.flatMap(_.toInt).toRight(s"Not a valid integer: $t")
  override def long(t: YAML): Either[String, Long] =
    t.asNumber.flatMap(_.toLong).toRight(s"Not a valid long: $t")
  override def double(t: YAML): Either[String, Double] =
    t.asNumber.map(_.toDouble).toRight(s"Not a valid double: $t")
  override def bool(t: YAML): Either[String, Boolean] =
    t.asBoolean.toRight(s"Not a valid boolean: $t")
  override def array(t: YAML): Either[String, Iterable[YAML]] =
    t.asArray.toRight(s"Not an array!")
  override def obj(t: YAML): Either[String, Iterable[(String, YAML)]] =
    t.asObject.toRight("Not an object!")
  override def opt(t: YAML): Option[YAML] = t match {
    case YNull => None
    case other => Some(other)
  }
}
