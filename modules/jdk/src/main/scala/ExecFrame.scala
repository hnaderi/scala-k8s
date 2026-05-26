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
package jdk

import dev.hnaderi.k8s.utils._
import io.k8s.apimachinery.pkg.apis.meta.v1
import org.typelevel.jawn.Facade
import org.typelevel.jawn.Parser

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

private[jdk] object ExecFrame {
  def encode(input: ExecInput): ByteBuffer = input match {
    case ExecInput.Stdin(data) =>
      val bb = ByteBuffer.allocate(data.length + 1)
      bb.put(0x00.toByte).put(data).flip()
      bb
    case ExecInput.Resize(cols, rows) =>
      val payload =
        s"""{"Width":$cols,"Height":$rows}"""
          .getBytes(StandardCharsets.UTF_8)
      val bb = ByteBuffer.allocate(payload.length + 1)
      bb.put(0x04.toByte).put(payload).flip()
      bb
  }

  def decode[T](data: Array[Byte])(implicit
      F: Facade.SimpleFacade[T],
      R: Reader[T]
  ): Option[ExecEvent] =
    if (data.isEmpty) None
    else
      (data(0) & 0xff) match {
        case 1 => Some(ExecEvent.Stdout(data.tail))
        case 2 => Some(ExecEvent.Stderr(data.tail))
        case 3 =>
          Parser
            .parseFromByteArray[T](data.tail)
            .toEither
            .flatMap(_.decodeTo[v1.Status])
            .toOption
            .map(ExecEvent.Error(_))
        case _ => None
      }
}
