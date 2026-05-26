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

import munit.FunSuite

import java.util.concurrent.CompletableFuture
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

class FutureBackendSuite extends FunSuite {

  test("toFuture: successful CompletableFuture completes Future with value") {
    val cf = CompletableFuture.completedFuture(42)
    assertEquals(Await.result(FutureBackend.toFuture(cf), 1.second), 42)
  }

  test("toFuture: failed CompletableFuture completes Future with exception") {
    val cf = new CompletableFuture[Int]()
    val boom = new RuntimeException("boom")
    cf.completeExceptionally(boom)
    val f = FutureBackend.toFuture(cf)
    Await.ready(f, 1.second).value match {
      case Some(Failure(e)) => assertEquals(e.getMessage, "boom")
      case Some(Success(v)) => fail(s"expected failure, got $v")
      case None             => fail("future not completed")
    }
  }

  test("toFuture: late completion is observed") {
    val cf = new CompletableFuture[String]()
    val f = FutureBackend.toFuture(cf)
    assert(!f.isCompleted)
    cf.complete("ok")
    assertEquals(Await.result(f, 1.second), "ok")
  }
}
