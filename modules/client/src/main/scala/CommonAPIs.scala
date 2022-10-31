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
import io.k8s.apimachinery.pkg.apis.meta.v1.APIGroupList
import io.k8s.apimachinery.pkg.apis.meta.v1.APIResourceList

import scala.concurrent.duration.FiniteDuration
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import CommonAPIs.selector

abstract class ListingRequest[O: Decoder, COL: Decoder](
    url: String,
    allowWatchBookmarks: Option[Boolean] = None,
    continue: Option[String] = None,
    fieldSelector: List[String] = Nil,
    labelSelector: List[String] = Nil,
    limit: Option[Int] = None,
    resourceVersion: Option[String] = None,
    resourceVersionMatch: Option[String] = None,
    timeout: Option[FiniteDuration] = None
) extends HttpRequest[COL]
    with WatchRequest[WatchEvent[O]] {

  private def params: Seq[(String, String)] = Seq(
    continue.map(c => "continue" -> c),
    selector("fieldSelector", fieldSelector),
    selector("labelSelector", labelSelector),
    limit.map(l => "limit" -> l.toString),
    resourceVersion.map(v => "resourceVersion" -> v),
    resourceVersionMatch.map(v => "resourceVersionMatch" -> v),
    timeout.map(t => "timeoutSeconds" -> t.toSeconds.toString)
  ).flatten

  private def watchParams =
    Seq("watch" -> "true") ++
      allowWatchBookmarks.map(b => "allowWatchBookmarks" -> b.toString) ++
      params

  override def send[F[_]](
      http: HttpClient[F]
  ): F[COL] = http.get(url, params: _*)
  override def listen[F[_]](http: StreamingClient[F]) =
    http.connect(url, watchParams: _*)
}

abstract class GetRequest[O: Decoder](url: String) extends HttpRequest[O] {
  override def send[F[_]](http: HttpClient[F]): F[O] = http.get(url)
}

abstract class CreateRequest[RES: Encoder: Decoder](
    url: String,
    body: RES,
    dryRun: Option[String] = None,
    fieldManager: Option[String] = None,
    fieldValidation: Option[String] = None
) extends HttpRequest[RES] {
  private def params: Seq[(String, String)] = Seq(
    dryRun.map("dryRun" -> _),
    fieldManager.map("fieldManager" -> _),
    fieldValidation.map("fieldValidation" -> _)
  ).flatten

  override def send[F[_]](
      http: HttpClient[F]
  ): F[RES] = http.post(url, params: _*)(body)
}

abstract class ReplaceRequest[RES: Encoder: Decoder](
    url: String,
    body: RES,
    dryRun: Option[String] = None,
    fieldManager: Option[String] = None,
    fieldValidation: Option[String] = None
) extends HttpRequest[RES] {

  private def params: Seq[(String, String)] = Seq(
    dryRun.map("dryRun" -> _),
    fieldManager.map("fieldManager" -> _),
    fieldValidation.map("fieldValidation" -> _)
  ).flatten

  override def send[F[_]](
      http: HttpClient[F]
  ): F[RES] = http.put(url, params: _*)(body)
}

abstract class PartialUpdateRequest[IN: Encoder, OUT: Decoder](
    url: String,
    body: IN,
    dryRun: Option[String] = None,
    fieldManager: Option[String] = None,
    fieldValidation: Option[String] = None,
    force: Option[Boolean] = None
) extends HttpRequest[OUT] {

  private def params: Seq[(String, String)] = Seq(
    dryRun.map("dryRun" -> _),
    fieldManager.map("fieldManager" -> _),
    fieldValidation.map("fieldValidation" -> _),
    force.map("force" -> _.toString)
  ).flatten

  override def send[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.patch(url, params: _*)(body)
}

abstract class DeleteCollectionRequest[OUT: Decoder](
    url: String,
    body: Option[DeleteOptions] = None,
    continue: Option[String] = None,
    dryRun: Option[String] = None,
    fieldSelector: List[String] = Nil,
    gracePeriodSeconds: Option[Int] = None,
    labelSelector: List[String] = Nil,
    limit: Option[Int] = None,
    propagationPolicy: Option[String] = None,
    resourceVersion: Option[String] = None,
    resourceVersionMatch: Option[String] = None,
    timeoutSeconds: Option[Int] = None
) extends HttpRequest[OUT] {

  private val params: Seq[(String, String)] = Seq(
    continue.map("continue" -> _),
    dryRun.map("dryRun" -> _),
    selector("fieldSelector", fieldSelector),
    gracePeriodSeconds.map("gracePeriodSeconds" -> _.toString),
    selector("labelSelector", labelSelector),
    limit.map("limit" -> _.toString),
    propagationPolicy.map("propagationPolicy" -> _),
    resourceVersion.map("resourceVersion" -> _),
    resourceVersionMatch.map("resourceVersionMatch" -> _),
    timeoutSeconds.map("timeoutSeconds" -> _.toString)
  ).flatten

  override def send[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.delete(url, params: _*)(body)
}

abstract class DeleteRequest[OUT: Decoder](
    url: String,
    body: Option[DeleteOptions] = None,
    dryRun: Option[String] = None,
    gracePeriodSeconds: Option[Int] = None,
    propagationPolicy: Option[String] = None
) extends HttpRequest[OUT] {

  private val params: Seq[(String, String)] = Seq(
    dryRun.map("dryRun" -> _),
    gracePeriodSeconds.map("gracePeriodSeconds" -> _.toString),
    propagationPolicy.map("propagationPolicy" -> _)
  ).flatten

  override def send[F[_]](
      http: HttpClient[F]
  ): F[OUT] = http.delete(url, params: _*)(body)
}

abstract class APIResourceListingRequest(url: String)
    extends HttpRequest[APIResourceList] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[APIResourceList] = http.get(url)
}

abstract class APIGroupListingRequest(url: String)
    extends HttpRequest[APIGroupList] {
  override def send[F[_]](
      http: HttpClient[F]
  ): F[APIGroupList] = http.get(url)
}

private object CommonAPIs {
  def selector(name: String, l: List[String]) =
    if (l.isEmpty) None
    else Some(name -> l.mkString(", "))
}
