package dev.hnaderi.k8s

import dev.hnaderi.k8s.utils._
import org.typelevel.jawn.Facade

package object jawn {
  implicit def jawnFacade[T](implicit
      builder: Builder[T]
  ): Facade.SimpleFacade[T] =
    new Facade.SimpleFacade[T] {
      override def jnull: T = builder.nil
      override def jfalse: T = builder.of(false)
      override def jtrue: T = builder.of(true)
      override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): T = {
        val n = BigDecimal(s.toString)
        if (n.isValidInt) builder.of(n.toIntExact)
        else if (n.isValidLong) builder.of(n.toLongExact)
        else builder.of(n.toDouble)
      }
      override def jstring(s: CharSequence): T = builder.of(s.toString)
      override def jarray(vs: List[T]): T = builder.arr(vs)
      override def jobject(vs: Map[String, T]): T = builder.obj(vs)
    }
}
