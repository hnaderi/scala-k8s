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

/** Adapter typeclass for building trees of type T that encode a subset of json,
  * required for k8s objects
  */
trait Builder[T] {
  def of(str: String): T
  def of(i: Int): T
  def of(l: Long): T
  def of(l: Double): T
  def of(b: Boolean): T
  def arr(a: Iterable[T]): T
  final def ofValues(a: T*): T = arr(a)
  def obj(values: Iterable[(String, T)]): T
  final def ofFields(values: (String, T)*): T = obj(values)
  def nil: T
}
object Builder {
  def apply[T](implicit t: Builder[T]) = t
}
