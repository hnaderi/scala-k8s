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

import scala.annotation.implicitNotFound

import Pointer.Plain

final case class PointerPath(parts: List[RefToken] = Nil) extends AnyVal {
  def /(p: RefToken): PointerPath = PointerPath(parts ++ Seq(p))
  def /(key: String): PointerPath = this / RefToken.Obj(key)
  def /(idx: Int): PointerPath = this / RefToken.Arr(idx)
  def last = this / RefToken.LastIdx

  final def toJsonPointer: String =
    if (parts.isEmpty) "" else s"/${parts.map(_.render).mkString("/")}"
}

sealed trait RefToken extends Serializable with Product {
  def render: String
}
object RefToken {
  final case class Obj(name: String) extends RefToken {
    def render: String = name.replaceAll("~", "~0").replaceAll("/", "~1")
  }
  final case class Arr(idx: Int) extends RefToken {
    def render: String = idx.toString
  }
  case object LastIdx extends RefToken {
    def render: String = "-"
  }
}

trait Pointer[+T] {
  def currentPath: PointerPath
}
object Pointer {
  final case class Plain[+A](currentPath: PointerPath = PointerPath())
      extends Pointer[A]

  final class Builder[T](private val dummy: Boolean = false) extends AnyVal {
    def root[P <: Pointer[T]](implicit p: Pointable[T, P]): P =
      p.point(PointerPath())
  }
  def apply[T]: Builder[T] = new Builder[T]

  val self = Plain()
}

final case class ListPointer[T](currentPath: PointerPath = PointerPath())
    extends Pointer[List[T]] {
  def at[P <: Pointer[T]](idx: Int)(implicit p: Pointable[T, P]): P =
    p.point(currentPath / idx)
  def last: Plain[T] = Plain(currentPath.last)
}

final case class MapPointer[T](currentPath: PointerPath = PointerPath())
    extends Pointer[Map[String, T]] {
  def at[P <: Pointer[T]](key: String)(implicit p: Pointable[T, P]): P =
    p.point(currentPath / key)
}

@implicitNotFound("Cannot find the root pointer for type ${T}!")
final case class Pointable[T, P <: Pointer[T]](point: PointerPath => P)
    extends AnyVal
object Pointable {
  implicit val intInstance: Pointable[Int, Plain[Int]] = Pointable(Plain(_))
  implicit val longInstance: Pointable[Long, Plain[Long]] = Pointable(
    Plain(_)
  )
  implicit val doubleInstance: Pointable[Double, Plain[Double]] = Pointable(
    Plain(_)
  )
  implicit val boolInstance: Pointable[Boolean, Plain[Boolean]] = Pointable(
    Plain(_)
  )
  implicit val stringInstance: Pointable[String, Plain[String]] = Pointable(
    Plain(_)
  )
}
