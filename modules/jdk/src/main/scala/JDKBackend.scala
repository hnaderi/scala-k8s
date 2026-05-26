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

import dev.hnaderi.k8s.jawn.jawnFacade
import dev.hnaderi.k8s.utils._
import io.k8s.apimachinery.pkg.apis.meta.v1
import org.typelevel.jawn.Facade
import org.typelevel.jawn.Parser

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class JDKBackend[T: Builder: Reader: Printer] protected (
    client: java.net.http.HttpClient
) extends HttpBackend[CompletableFuture] {
  protected implicit val jawnF: Facade.SimpleFacade[T] = jawnFacade[T]

  override def send[O: Decoder](
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): CompletableFuture[O] = {
    val req = JDKBackend
      .request(url, verb, headers, params, cookies, None)
      .build()
    sendAndDecode[O](req)
  }

  override def send[I: Encoder, O: Decoder](
      url: String,
      verb: APIVerb,
      body: I,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)]
  ): CompletableFuture[O] = {
    val payload = Printer[T].print(body.encodeTo[T])
    val req = JDKBackend
      .request(url, verb, headers, params, cookies, Some(payload))
      .build()
    sendAndDecode[O](req)
  }

  private def sendAndDecode[O: Decoder](
      req: HttpRequest
  ): CompletableFuture[O] =
    client
      .sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
      .thenApply[O] { resp =>
        val body = resp.body()
        if (resp.statusCode() >= 200 && resp.statusCode() < 300)
          Parser
            .parseFromByteArray[T](body)
            .toEither
            .left
            .map(e => new JDKBackend.DecodeError(e.getMessage))
            .flatMap(
              _.decodeTo[O].left.map(JDKBackend.DecodeError(_))
            )
            .fold(throw _, identity)
        else throw JDKBackend.toErrorResponse(resp.statusCode(), body)
      }
}

object JDKBackend {
  final case class DecodeError(msg: String) extends Exception(msg)

  def apply[T: Builder: Reader: Printer](
      client: java.net.http.HttpClient
  ): JDKBackend[T] = new JDKBackend[T](client)

  private[jdk] def toErrorResponse[T: Builder: Reader](
      status: Int,
      body: Array[Byte]
  ): Throwable = {
    val err = status match {
      case 409 => ErrorStatus.Conflict
      case 404 => ErrorStatus.NotFound
      case 401 => ErrorStatus.Unauthorized
      case 403 => ErrorStatus.Forbidden
      case 400 => ErrorStatus.BadRequest
      case s   => ErrorStatus.Other(s)
    }
    implicit val facade: Facade.SimpleFacade[T] = jawnFacade[T]
    Parser
      .parseFromByteArray[T](body)
      .toEither
      .left
      .map(e => new DecodeError(e.getMessage))
      .flatMap(_.decodeTo[v1.Status].left.map(DecodeError(_)))
      .fold(identity, status => ErrorResponse(err, status))
  }

  private[jdk] def request(
      url: String,
      verb: APIVerb,
      headers: Seq[(String, String)],
      params: Seq[(String, String)],
      cookies: Seq[(String, String)],
      body: Option[String]
  ): HttpRequest.Builder = {
    val builder =
      HttpRequest.newBuilder().uri(URI.create(buildUrl(url, params)))
    headers.foreach { case (k, v) => builder.header(k, v) }
    if (cookies.nonEmpty) {
      builder.header("Cookie", cookieHeader(cookies))
      ()
    }
    builder.header("Content-Type", contentTypeFor(verb))

    val bodyPublisher = body match {
      case Some(b) =>
        HttpRequest.BodyPublishers.ofString(b, StandardCharsets.UTF_8)
      case None => HttpRequest.BodyPublishers.noBody()
    }
    verb match {
      case APIVerb.GET    => builder.GET()
      case APIVerb.POST   => builder.POST(bodyPublisher)
      case APIVerb.PUT    => builder.PUT(bodyPublisher)
      case APIVerb.DELETE =>
        body match {
          case Some(_) => builder.method("DELETE", bodyPublisher)
          case None    => builder.DELETE()
        }
      case APIVerb.PATCH(_) => builder.method("PATCH", bodyPublisher)
    }
    builder
  }

  private[jdk] def contentTypeFor(verb: APIVerb): String = verb match {
    case APIVerb.PATCH(ptype) => ptype.contentType
    case _                    => "application/json"
  }

  private[jdk] def cookieHeader(cookies: Seq[(String, String)]): String =
    cookies.map { case (k, v) => s"$k=$v" }.mkString("; ")

  private[jdk] def buildUrl(
      base: String,
      params: Seq[(String, String)]
  ): String =
    if (params.isEmpty) base
    else {
      val qs = params
        .map { case (k, v) =>
          s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder
              .encode(v, StandardCharsets.UTF_8)}"
        }
        .mkString("&")
      val sep = if (base.contains('?')) "&" else "?"
      s"$base$sep$qs"
    }
}
