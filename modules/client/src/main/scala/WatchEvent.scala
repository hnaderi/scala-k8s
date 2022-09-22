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

package dev.hnaderi.k8s.client

import dev.hnaderi.k8s.utils._

final case class WatchEvent[+T](
    event: WatchEventType,
    payload: T
)
object WatchEvent {
  implicit def encoder[A: Encoder]: Encoder[WatchEvent[A]] =
    new Encoder[WatchEvent[A]] {
      def apply[T: Builder](r: WatchEvent[A]): T = {
        val obj = ObjectWriter[T]()
        obj
          .write("type", r.event)
          .write("object", r.payload)
          .build
      }
    }

  implicit def decoder[A: Decoder]: Decoder[WatchEvent[A]] =
    new Decoder[WatchEvent[A]] {
      def apply[T: Reader](t: T): Either[String, WatchEvent[A]] = for {
        obj <- ObjectReader(t)
        tpe <- obj.read[WatchEventType]("type")
        pl <- obj.read[A]("object")
      } yield WatchEvent(tpe, pl)
    }
}

sealed trait WatchEventType extends Serializable with Product
object WatchEventType {
  case object ADDED extends WatchEventType
  case object DELETED extends WatchEventType
  case object MODIFIED extends WatchEventType
  case object BOOKMARK extends WatchEventType
  case object ERROR extends WatchEventType
  final case class Unknown(value: String) extends WatchEventType

  implicit val encodeEventType: Encoder[WatchEventType] =
    Encoder[String].contramap {
      case ADDED          => "ADDED"
      case DELETED        => "DELETED"
      case MODIFIED       => "MODIFIED"
      case BOOKMARK       => "BOOKMARK"
      case ERROR          => "ERROR"
      case Unknown(value) => value
    }

  implicit val decodeEventType: Decoder[WatchEventType] = Decoder[String].emap {
    case "ADDED"    => Right(ADDED)
    case "DELETED"  => Right(DELETED)
    case "MODIFIED" => Right(MODIFIED)
    case "BOOKMARK" => Right(BOOKMARK)
    case "ERROR"    => Right(ERROR)
    case other      => Right(Unknown(other))
  }
}
