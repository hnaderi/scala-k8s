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
package zio

import dev.hnaderi.k8s.jawn.jawnFacade
import dev.hnaderi.k8s.utils._
import dev.hnaderi.k8s.zioJson._
import org.typelevel.jawn.AsyncParser
import _root_.zio._
import _root_.zio.http._
import _root_.zio.json.ast.Json
import _root_.zio.stream._

class ZIOStreamingBackend(client: Client, auth: Task[AuthenticationParams])
    extends ZIOBackend(client, auth)
    with StreamingBackend[ZKStream] {

  override def connectRaw(
      url: String,
      verb: APIVerb,
      params: Seq[(String, String)]
  ): ZKStream[Byte] =
    ZStream.unwrapScoped {
      for {
        a <- auth
        u <- urlFor(url, params ++ a.params)
        con <- contentType(verb)
        req = Request(
          method = methodFor(verb),
          url = u,
          body = Body.empty,
          headers =
            Headers(con) ++ headersFor(a.headers) ++ cookiesFor(a.cookies),
          version = Version.`HTTP/1.1`,
          remoteAddress = None
        )
        res <- ZClient
          .streaming(req)
          .provideSomeEnvironment[Scope](_.add[Client](client))
      } yield res.body.asStream
    }

  override def connectLines(
      url: String,
      verb: APIVerb,
      params: Seq[(String, String)]
  ): ZKStream[String] =
    connectRaw(url, verb, params)
      .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
      .filter(_.nonEmpty)

  override def connect[O: Decoder](
      url: String,
      verb: APIVerb,
      params: Seq[(String, String)]
  ): ZKStream[O] = {
    val raw = connectRaw(url, verb, params)
    ZStream
      .fromZIO(ZIO.succeed(AsyncParser[Json](AsyncParser.ValueStream)))
      .flatMap { parser =>
        raw.chunks.flatMap { chunk =>
          parser.absorb(chunk.toArray)(jawnFacade[Json]) match {
            case Left(err) =>
              ZStream.fail(new Exception(s"JSON parse error: $err"))
            case Right(values) =>
              ZStream.fromIterable(values).mapZIO { json =>
                ZIO.fromEither(json.decodeTo[O].left.map(new Exception(_)))
              }
          }
        }
      }
  }
}

object ZIOStreamingBackend {
  def make: ZLayer[Client, Nothing, ZIOStreamingBackend] =
    Scope.default >>> ZLayer {
      ZIO
        .service[Client]
        .map(
          new ZIOStreamingBackend(_, ZIO.succeed(AuthenticationParams.empty))
        )
    }
}
