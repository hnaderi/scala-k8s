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

import dev.hnaderi.k8s.circe._
import io.circe.Json
import munit.FunSuite

class JDKBackendSuite extends FunSuite {

  test("buildUrl: no params returns base unchanged") {
    assertEquals(
      JDKBackend.buildUrl("https://example.com/api", Nil),
      "https://example.com/api"
    )
  }

  test("buildUrl: appends ? when base has no query") {
    assertEquals(
      JDKBackend.buildUrl("https://example.com/api", Seq("a" -> "1")),
      "https://example.com/api?a=1"
    )
  }

  test("buildUrl: appends & when base already has a query") {
    assertEquals(
      JDKBackend.buildUrl("https://example.com/api?x=y", Seq("a" -> "1")),
      "https://example.com/api?x=y&a=1"
    )
  }

  test("buildUrl: percent-encodes keys and values") {
    assertEquals(
      JDKBackend.buildUrl(
        "https://example.com/api",
        Seq("field selector" -> "a=b/c d")
      ),
      "https://example.com/api?field+selector=a%3Db%2Fc+d"
    )
  }

  test("buildUrl: preserves repeated keys") {
    assertEquals(
      JDKBackend.buildUrl(
        "https://example.com/api",
        Seq("k" -> "1", "k" -> "2")
      ),
      "https://example.com/api?k=1&k=2"
    )
  }

  test("cookieHeader: joins with semicolon") {
    assertEquals(
      JDKBackend.cookieHeader(Seq("a" -> "1", "b" -> "2")),
      "a=1; b=2"
    )
  }

  test("cookieHeader: single cookie") {
    assertEquals(JDKBackend.cookieHeader(Seq("a" -> "1")), "a=1")
  }

  test("contentTypeFor: GET/POST/PUT/DELETE are application/json") {
    assertEquals(JDKBackend.contentTypeFor(APIVerb.GET), "application/json")
    assertEquals(JDKBackend.contentTypeFor(APIVerb.POST), "application/json")
    assertEquals(JDKBackend.contentTypeFor(APIVerb.PUT), "application/json")
    assertEquals(JDKBackend.contentTypeFor(APIVerb.DELETE), "application/json")
  }

  test("contentTypeFor: PATCH uses patch type's content type") {
    assertEquals(
      JDKBackend.contentTypeFor(APIVerb.PATCH(PatchType.JsonPatch)),
      "application/json-patch+json"
    )
    assertEquals(
      JDKBackend.contentTypeFor(APIVerb.PATCH(PatchType.Merge)),
      "application/merge-patch+json"
    )
    assertEquals(
      JDKBackend.contentTypeFor(APIVerb.PATCH(PatchType.StrategicMerge)),
      "application/strategic-merge-patch+json"
    )
    assertEquals(
      JDKBackend.contentTypeFor(APIVerb.PATCH(PatchType.ServerSide)),
      "application/apply-patch+yaml"
    )
  }

  private def statusBody(reason: String): Array[Byte] =
    s"""{"kind":"Status","apiVersion":"v1","status":"Failure","reason":"$reason"}"""
      .getBytes(
        "UTF-8"
      )

  test("toErrorResponse: each status maps to the right ErrorStatus") {
    val cases = Map(
      404 -> ErrorStatus.NotFound,
      409 -> ErrorStatus.Conflict,
      401 -> ErrorStatus.Unauthorized,
      403 -> ErrorStatus.Forbidden,
      400 -> ErrorStatus.BadRequest,
      418 -> ErrorStatus.Other(418)
    )
    cases.foreach { case (code, expected) =>
      val result =
        JDKBackend.toErrorResponse[Json](code, statusBody("NoReason"))
      assert(result.isInstanceOf[ErrorResponse], s"got ${result.getClass}")
      val er = result.asInstanceOf[ErrorResponse]
      assertEquals(er.error, expected)
    }
  }

  test("toErrorResponse: decodes v1.Status reason from body") {
    val result =
      JDKBackend.toErrorResponse[Json](404, statusBody("NotFound"))
    assertEquals(
      result.asInstanceOf[ErrorResponse].details.reason,
      Some("NotFound")
    )
  }

  test("toErrorResponse: malformed body yields DecodeError") {
    val result = JDKBackend.toErrorResponse[Json](404, "not json".getBytes)
    assert(
      result.isInstanceOf[JDKBackend.DecodeError],
      s"got ${result.getClass}"
    )
  }

  test("request: GET builds correct method, content-type, headers, cookies") {
    val r = JDKBackend
      .request(
        url = "https://example.com/api",
        verb = APIVerb.GET,
        headers = Seq("X-Foo" -> "bar"),
        params = Seq("a" -> "1"),
        cookies = Seq("c" -> "v"),
        body = None
      )
      .build()
    assertEquals(r.method(), "GET")
    assertEquals(r.uri().toString, "https://example.com/api?a=1")
    val hs = r.headers()
    assertEquals(hs.firstValue("X-Foo").get(), "bar")
    assertEquals(hs.firstValue("Cookie").get(), "c=v")
    assertEquals(hs.firstValue("Content-Type").get(), "application/json")
  }

  test("request: POST with body sets method and body length > 0") {
    val r = JDKBackend
      .request(
        url = "https://example.com/api",
        verb = APIVerb.POST,
        headers = Nil,
        params = Nil,
        cookies = Nil,
        body = Some("""{"x":1}""")
      )
      .build()
    assertEquals(r.method(), "POST")
    assert(r.bodyPublisher().get().contentLength() > 0)
  }

  test("request: DELETE with body uses method('DELETE', publisher)") {
    val r = JDKBackend
      .request(
        url = "https://example.com/api",
        verb = APIVerb.DELETE,
        headers = Nil,
        params = Nil,
        cookies = Nil,
        body = Some("""{"x":1}""")
      )
      .build()
    assertEquals(r.method(), "DELETE")
    assert(r.bodyPublisher().get().contentLength() > 0)
  }

  test("request: PATCH uses patch-specific content type") {
    val r = JDKBackend
      .request(
        url = "https://example.com/api",
        verb = APIVerb.PATCH(PatchType.Merge),
        headers = Nil,
        params = Nil,
        cookies = Nil,
        body = Some("""{"x":1}""")
      )
      .build()
    assertEquals(r.method(), "PATCH")
    assertEquals(
      r.headers().firstValue("Content-Type").get(),
      "application/merge-patch+json"
    )
  }
}
