package dev.hnaderi.k8s.utils

/** Adapter typeclass for building trees of type ${T} that encode a subset of
  * json, required for k8s objects
  */
trait Builder[T] {
  def of(str: String): T
  def of(i: Int): T
  def of(l: Long): T
  def of(l: Double): T
  def of(b: Boolean): T
  def arr(a: Iterable[T]): T
  final def ofValues(a: T*): T = arr(a)
  def obj(values: Iterable[(String, T)]): T
  final def ofFields(values: (String, T)*): T = obj(values)
}
object Builder {
  def apply[T](implicit t: Builder[T]) = t
}
