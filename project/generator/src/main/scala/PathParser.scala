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

package dev.hnaderi.k8s.generator

import io.circe.Error
import io.circe.JsonObject
import io.circe.parser.parse

import java.io.File
import scala.util.matching.Regex

object PathParser {

  private val nsResourcePattern: Regex =
    """^(/api(?:s/[^/]+)?/v[^/]+)/namespaces/\{namespace\}/([^/]+)$""".r
  private val clusterResourcePattern: Regex =
    """^(/api(?:s/[^/]+)?/v[^/]+)/([^/{]+)$""".r
  private val nsSubResourcePattern: Regex =
    """^/api(?:s/[^/]+)?/v[^/]+/namespaces/\{namespace\}/([^/]+)/\{name\}/([^/{]+)$""".r
  private val clusterSubResourcePattern: Regex =
    """^/api(?:s/[^/]+)?/v[^/]+/([^/{]+)/\{name\}/([^/{]+)$""".r

  def loadResources(
      file: File,
      defs: Map[String, Definition]
  ): Either[Error, Seq[ResourceInfo]] =
    parse(Utils.loadFile(file)).map { json =>
      val paths = json.hcursor
        .downField("paths")
        .focus
        .flatMap(_.asObject)
        .getOrElse(JsonObject.empty)

      // GVK triplet -> fully-qualified definition name
      val gvkIndex: Map[(String, String, String), String] =
        defs.flatMap { case (fqName, defn) =>
          defn.`x-kubernetes-group-version-kind`.getOrElse(Nil).map { k =>
            (k.group, k.version, k.kind) -> fqName
          }
        }

      // resource key -> GVK
      type ResourceKey = (String, String, Boolean) // (path, resourceName, isNs)
      val resourceGVKs =
        scala.collection.mutable.Map[ResourceKey, Kind]()

      // sub-resource key -> actions
      type SubKey =
        (String, String, Boolean) // (resourceName, subresource, isNs)
      val subActions =
        scala.collection.mutable
          .Map[SubKey, scala.collection.mutable.Set[String]]()

      paths.toList.foreach { case (path, pathJson) =>
        pathJson.asObject.getOrElse(JsonObject.empty).toList.foreach {
          case ("parameters", _) => ()
          case (_, opJson)       =>
            val op = opJson.hcursor
            val gvkOpt = op
              .downField("x-kubernetes-group-version-kind")
              .as[Kind]
              .toOption
            val actionOpt = op
              .downField("x-kubernetes-action")
              .as[String]
              .toOption
              .filter(_.nonEmpty)

            gvkOpt.foreach { gvk =>
              path match {
                case nsResourcePattern(_, res) =>
                  resourceGVKs.getOrElseUpdate((path, res, true), gvk)
                case clusterResourcePattern(_, res) =>
                  resourceGVKs.getOrElseUpdate((path, res, false), gvk)
                case _ => ()
              }
              actionOpt.foreach { action =>
                path match {
                  case nsSubResourcePattern(res, sub) =>
                    subActions
                      .getOrElseUpdate(
                        (res, sub, true),
                        scala.collection.mutable.Set()
                      )
                      .add(action)
                  case clusterSubResourcePattern(res, sub) =>
                    subActions
                      .getOrElseUpdate(
                        (res, sub, false),
                        scala.collection.mutable.Set()
                      )
                      .add(action)
                  case _ => ()
                }
              }
            }
        }
      }

      // Merge namespaced and cluster entries for the same (group, version, resource)
      type CanonKey = (String, String, String) // (group, version, resourceName)
      val canonical =
        scala.collection.mutable.Map[CanonKey, (Kind, Boolean)]()

      resourceGVKs.foreach { case ((_, res, isNs), gvk) =>
        val key: CanonKey = (gvk.group, gvk.version, res)
        canonical.get(key) match {
          case None                  => canonical(key) = (gvk, isNs)
          case Some((_, existingNs)) =>
            if (isNs && !existingNs) canonical(key) = (gvk, true)
        }
      }

      canonical.toSeq.flatMap { case ((group, version, res), (gvk, isNs)) =>
        val kind = gvk.kind
        val listKind = kind + "List"
        for {
          fqKind <- gvkIndex.get((group, version, kind))
          fqListKind <- gvkIndex.get((group, version, listKind))
        } yield {
          val subs = subActions.keys
            .filter(k => k._1 == res)
            .map(_._2)
            .toSet
            .toSeq
            .sorted
            .map { subName =>
              val nsA = subActions
                .get((res, subName, true))
                .fold(Set.empty[String])(_.toSet)
              val clA = subActions
                .get((res, subName, false))
                .fold(Set.empty[String])(_.toSet)
              SubResourceInfo(subName, nsA ++ clA)
            }

          ResourceInfo(
            group = group,
            version = version,
            kind = kind,
            listKind = listKind,
            fqKind = fqKind.replace('-', '_'),
            fqListKind = fqListKind.replace('-', '_'),
            resourceName = res,
            isNamespaced = isNs,
            subResources = subs
          )
        }
      }
    }
}
