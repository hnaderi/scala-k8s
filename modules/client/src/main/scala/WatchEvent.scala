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
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import io.k8s.apimachinery.pkg.apis.meta.v1.Status

/** A single event of a watch stream.
  *
  * The `object` field of a watch event does not always hold the watched
  * resource; its shape depends on the event type, so each case carries only
  * what kubernetes actually sends:
  *
  *   - `ADDED`, `MODIFIED` and `DELETED` carry the resource itself
  *   - `BOOKMARK` carries a stub object holding nothing but a resource version
  *   - `ERROR` carries a [[io.k8s.apimachinery.pkg.apis.meta.v1.Status]]
  */
sealed trait WatchEvent[+T] extends Serializable with Product {
  def eventType: WatchEventType
}

object WatchEvent {

  /** The watched resource was created, or already existed when the watch
    * started.
    */
  final case class Added[+T](payload: T) extends WatchEvent[T] {
    def eventType: WatchEventType = WatchEventType.ADDED
  }

  /** The watched resource was modified. */
  final case class Modified[+T](payload: T) extends WatchEvent[T] {
    def eventType: WatchEventType = WatchEventType.MODIFIED
  }

  /** The watched resource was deleted; the payload is its last known state. */
  final case class Deleted[+T](payload: T) extends WatchEvent[T] {
    def eventType: WatchEventType = WatchEventType.DELETED
  }

  /** A checkpoint, sent only when `allowWatchBookmarks` is requested.
    *
    * It carries no resource; kubernetes sends an otherwise empty object of the
    * watched kind, whose only purpose is to report a resource version that the
    * watch can later be resumed from.
    */
  final case class Bookmark(metadata: ObjectMeta) extends WatchEvent[Nothing] {
    def eventType: WatchEventType = WatchEventType.BOOKMARK
    def resourceVersion: Option[String] = metadata.resourceVersion
  }

  /** The watch failed, typically with a `410 Gone` status when the requested
    * resource version is too old to resume from.
    */
  final case class Error(status: Status) extends WatchEvent[Nothing] {
    def eventType: WatchEventType = WatchEventType.ERROR
  }

  /** An event type that this client does not know about; its object is ignored,
    * as its shape cannot be known.
    */
  final case class Other(eventType: WatchEventType.Unknown)
      extends WatchEvent[Nothing]

  implicit def encoder[A: Encoder]: Encoder[WatchEvent[A]] =
    new Encoder[WatchEvent[A]] {
      def apply[T: Builder](r: WatchEvent[A]): T = {
        val payload: T = r match {
          case Added(p)    => p.encodeTo[T]
          case Modified(p) => p.encodeTo[T]
          case Deleted(p)  => p.encodeTo[T]
          case Bookmark(m) => ObjectWriter[T]().write("metadata", m).build
          case Error(s)    => s.encodeTo[T]
          case Other(_)    => ObjectWriter[T]().build
        }

        Builder[T].obj(
          List("type" -> r.eventType.encodeTo[T], "object" -> payload)
        )
      }
    }

  implicit def decoder[A: Decoder]: Decoder[WatchEvent[A]] =
    new Decoder[WatchEvent[A]] {
      def apply[T: Reader](t: T): Either[String, WatchEvent[A]] = for {
        obj <- ObjectReader(t)
        tpe <- obj.read[WatchEventType]("type")
        ev <- eventFrom(obj, tpe)
      } yield ev

      private def eventFrom[T: Reader](
          obj: ObjectReader[T],
          tpe: WatchEventType
      ): Either[String, WatchEvent[A]] = tpe match {
        case WatchEventType.ADDED    => obj.read[A]("object").map(Added(_))
        case WatchEventType.MODIFIED => obj.read[A]("object").map(Modified(_))
        case WatchEventType.DELETED  => obj.read[A]("object").map(Deleted(_))
        case WatchEventType.BOOKMARK =>
          obj
            .get("object")
            .flatMap(ObjectReader(_))
            .flatMap(_.readOpt[ObjectMeta]("metadata"))
            .map(meta => Bookmark(meta.getOrElse(ObjectMeta())))
        case WatchEventType.ERROR => obj.read[Status]("object").map(Error(_))
        case u: WatchEventType.Unknown => Right(Other(u))
      }
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
