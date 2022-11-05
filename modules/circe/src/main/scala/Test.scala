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

// /*
//  * Copyright 2022 Hossein Naderi
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package test

// import dev.hnaderi.k8s.Havij
// import io.circe.Json
// import dev.hnaderi.k8s.utils._

// trait Visitor[R, T] {
//   def visit(str: String): T
//   def visit(i: Int): T
//   def visit(l: Long): T
//   def visit(b: Boolean): T
//   def visitArr(a: Iterable[R]): T
//   def visitObj(values: Iterable[(String, R)]): T
// }

// trait Values {
//   import JS._
//   val a = JStr("abc")
//   val b = JArr(JInt(1), JStr("def"))
//   val c = JObj(
//     "a" -> a,
//     "b" -> b,
//     "bb" -> JObj("b" -> b)
//   )
//   val l = List(a, b, c)
// }

// object Instances {
//   implicit val jsonMapping: Builder[Json] = new Builder[Json] {

//     override def of(str: String): Json = Json.fromString(str)

//     override def of(i: Int): Json = Json.fromInt(i)

//     override def of(l: Long): Json = Json.fromLong(l)

//     override def of(l: Double): Json = Json.fromDoubleOrString(l)

//     override def of(b: Boolean): Json = Json.fromBoolean(b)

//     override def arr(a: Iterable[Json]): Json = Json.fromValues(a)

//     override def obj(values: Iterable[(String, Json)]): Json =
//       Json.fromFields(values)
//     override def nil = Json.Null
//   }

//   implicit val jsMapping: Builder[JS] = new Builder[JS] {

//     override def of(str: String): JS = JS.JStr(str)

//     override def of(i: Int): JS = JS.JInt(i)

//     override def of(l: Long): JS = JS.JInt(l.toInt)

//     override def of(l: Double): JS = JS.JInt(l.toInt)

//     override def of(b: Boolean): JS = JS.JBool(b)

//     override def arr(a: Iterable[JS]): JS = JS.JArr(a.toSeq: _*)

//     override def obj(values: Iterable[(String, JS)]): JS =
//       JS.JObj(values.toSeq: _*)
//     override def nil: JS = JS.JObj()
//   }

//   implicit val jsonReader: Reader[Json] = new Reader[Json] {

//     override def string(t: Json): Either[String, String] =
//       t.asString.toRight("Not an string")

//     override def int(t: Json): Either[String, Int] =
//       t.as[Int].left.map(_.getMessage())

//     override def long(t: Json): Either[String, Long] =
//       t.as[Long].left.map(_.getMessage())

//     override def double(t: Json): Either[String, Double] =
//       t.as[Double].left.map(_.getMessage())

//     override def bool(t: Json): Either[String, Boolean] =
//       t.asBoolean.toRight("Not a boolean")

//     override def array(t: Json): Either[String, Iterable[Json]] =
//       t.asArray.toRight("Not an array")

//     override def obj(t: Json): Either[String, Iterable[(String, Json)]] =
//       t.asObject.map(_.toMap).toRight("Not an object")

//   }

// }

// object Test extends App with Values {
//   // l.map(_.visit(Stringify)).foreach(println)
//   // l.map(_.visit(Yamlify())).foreach(println)
//   println(c.visit(Yamlify()))
// }

// object Experiment extends App {
//   import Instances._
//   def hn(i: Int): Havij = Havij(1, "b", if (i > 0) Some(hn(i - 1)) else None)

//   val newValue = hn(3)
//   val json = newValue.encodeTo[Json]
//   println(json.spaces2)

//   val read = json.decodeTo[Havij]
//   println(read)
//   // println(newValue.to[JS].visit(Yamlify()))
// }

// object Stringify extends Visitor[JS, String] {
//   def visit(i: Int): String = i.toString()
//   def visit(l: Long): String = l.toString()
//   def visit(str: String): String = s""""$str""""
//   def visit(b: Boolean): String = if (b) "true" else "false"
//   def visitArr(a: Iterable[JS]): String = s"[${a.mkString(",")}]"
//   def visitObj(values: Iterable[(String, JS)]): String = {
//     val str = values.map { case (k, v) => s""""${k}" : $v""" }.mkString(",")
//     s"{$str}"
//   }
// }

// case class Yamlify(spaces: Int = 2, level: Int = 0)
//     extends Visitor[JS, String] {
//   def visit(i: Int): String = i.toString()
//   def visit(l: Long): String = l.toString()
//   def visit(str: String): String = str
//   def visit(b: Boolean): String = if (b) "true" else "false"
//   def visitArr(a: Iterable[JS]): String = {
//     val inner = copy(level = level + spaces)
//     val o = a.map(s => " " * level + "- " + s.visit(inner)).mkString("\n")
//     if (level == 0) o else s"\n$o"
//   }
//   def visitObj(values: Iterable[(String, JS)]): String = {
//     val inner = copy(level = level + spaces)
//     val o = values
//       .map { case (k, v) =>
//         " " * level + k + ": " + v.visit(inner)
//       }
//       .mkString("\n")
//     if (level == 0) o else s"\n$o"
//   }
// }

// sealed trait JS extends Serializable with Product {
//   def visit[T](v: Visitor[JS, T]): T
// }

// object JS {
//   final case class JStr(s: String) extends JS {
//     def visit[T](v: Visitor[JS, T]): T = v.visit(s)
//   }
//   final case class JInt(s: Int) extends JS {
//     def visit[T](v: Visitor[JS, T]): T = v.visit(s)
//   }
//   final case class JObj(values: (String, JS)*) extends JS {
//     def visit[T](vis: Visitor[JS, T]): T = vis.visitObj(values)
//   }
//   final case class JArr(arr: JS*) extends JS {
//     def visit[T](v: Visitor[JS, T]): T = v.visitArr(arr)
//   }

//   final case class JBool(b: Boolean) extends JS {
//     def visit[T](v: Visitor[JS, T]): T = v.visit(b)
//   }

// }
