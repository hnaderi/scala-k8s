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

package object utils {
  implicit class EncoderOps[R](val r: R) extends AnyVal {
    def encodeTo[T](implicit enc: Encoder[R, T]): T = enc(r)
  }
  implicit class DecoderOps[T](val t: T) extends AnyVal {
    def decodeTo[R](implicit dec: Decoder[T, R]): Either[String, R] = dec(t)
  }
}
