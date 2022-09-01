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

import java.io.File

object Main extends App {
  val base = "/storage/projects/personal/sbt-k8s"
  val baseDir = new File(base)
  val swaggerFile = new File(s"$base/swagger.json")
  val codeDir = new File(s"$base/objects/src/main/scala/generated")

  val defs = Utils.loadDefinitions(swaggerFile)
  val dd = defs.getOrElse(Map.empty)

  // defs match {
  //   case Left(err)=> Console.err.println(err.getMessage())
  //   case Right(defs)=> defs.foreach{ case (k, v) =>
  //     println(s"Definition: $k")
  //     println(v)
  //   }
  // }

  // dd
  //   .filterNot(_._2.`type` == Some("object"))
  //   .foreach { case (k, v) =>
  //     println(k)
  //     println(v.`type`)
  //     println(v.`x-kubernetes-group-version-kind`)
  //     println(v.description)
  //     println()
  //   }

  // val resources = dd.filter { case (k, v) =>
  //   v.`x-kubernetes-group-version-kind`.exists(_.size >= 1)
  // }

  // resources.filterNot { case (name, pr) =>
  //   val props = pr.properties.getOrElse(Map.empty)
  //   pr.`x-kubernetes-group-version-kind`.isDefined &&
  //   props.contains("kind") &&
  //   props.contains("apiVersion")
  // }.map(_._1).foreach(println)

  // resources.foreach { case (k, v) =>
  //   println(k)
  //   println(v.`x-kubernetes-group-version-kind`)
  //   println()
  // }

  // val kinds =
  //   dd.values.flatMap(_.`x-kubernetes-group-version-kind`).flatten.toVector
  // println(kinds.size)
  // println(kinds.toSet.size)

//   dd.values
//     .flatMap(_.properties)
//     .flatten
//     .filter(_._2.additionalProperties.isDefined)
//     .foreach { case (str, prop) =>
//       println(s"""
// Name: $str
// Type: ${prop.`type`}
// Values: ${prop.additionalProperties.flatMap(p => p.`type`.orElse(p.$ref))}
// """)
//     }

  val sources = dd.map(SourceCode(_))
  sources.foreach(_.write(codeDir))
}
