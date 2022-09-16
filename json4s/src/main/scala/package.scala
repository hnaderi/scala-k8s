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

import dev.hnaderi.k8s.utils._
import org.json4s.JValue
import org.json4s.MappingException
import org.json4s.Writer

package object json4s {
  implicit val json4sBuilder: Builder[JValue] = Json4sBuilder
  implicit val json4sReader: Reader[JValue] = Json4sReader
  implicit def k8sJsonWriter[T](implicit enc: Encoder[T, JValue]): Writer[T] =
    new Writer[T] {
      def write(obj: T): JValue = enc(obj)
    }
  implicit def k8sJsonReader[T](implicit
      dec: Decoder[JValue, T]
  ): org.json4s.Reader[T] = new org.json4s.Reader[T] {
    def readEither(value: JValue): Either[MappingException, T] =
      dec(value).left.map(new MappingException(_))
  }

}
