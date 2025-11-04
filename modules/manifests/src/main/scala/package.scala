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
import dev.hnaderi.yaml4s.Backend
import dev.hnaderi.yaml4s.YAML

import scala.annotation.tailrec

package object manifest {
  implicit val yamlReader: Reader[YAML] = KObjectYAMLReader
  implicit val yamlBuilder: Builder[YAML] = KObjectYAMLBuilder

  implicit class KObjectsOps(val objs: Iterable[KObject]) extends AnyVal {
    def asManifest: String =
      Backend.printDocuments(objs.map(_.foldTo[YAML]))
  }

  implicit class KObjectOps(val obj: KObject) extends AnyVal {
    def asManifest: String = Backend.print(obj.foldTo[YAML])
  }

  private def toKObject[T](
      y: YAML
  )(implicit dec: Decoder[T]): Either[Throwable, T] =
    dec.apply(y) match {
      case Left(value)  => Left(new Exception(value))
      case e @ Right(_) => e.asInstanceOf[Either[Throwable, T]]
    }

  private def toKObject[T](
      docs: Iterable[YAML]
  )(implicit dec: Decoder[T]): Either[Throwable, List[T]] = {
    import collection.mutable.ListBuffer
    val out = ListBuffer.empty[T]

    @tailrec
    def go(
        error: Option[Throwable],
        it: Iterator[YAML]
    ): Either[Throwable, List[T]] = error match {
      case Some(value)        => Left(value)
      case None if it.hasNext =>
        toKObject(it.next()) match {
          case Left(err)   => Left(err)
          case Right(kobj) =>
            out.append(kobj)
            go(None, it)
        }
      case _ => Right(out.result())
    }

    go(None, docs.iterator)
  }

  /** Parses a single document manifest
    */
  def parse[T: Decoder](str: String): Either[Throwable, T] =
    Backend
      .parse[YAML](str)
      .flatMap(toKObject[T])

  /** Parses a possibly multi document manifest
    */
  def parseAll[T: Decoder](str: String): Either[Throwable, List[T]] =
    Backend.parseDocuments[YAML](str).flatMap(toKObject[T])

  /** Parses a single document manifest
    */
  def parseObj(str: String): Either[Throwable, KObject] =
    parse[KObject](str)

  /** Parses a possibly multi document manifest
    */
  def parseAllObjects(str: String): Either[Throwable, List[KObject]] =
    parseAll[KObject](str)
}
