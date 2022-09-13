package dev.hnaderi.k8s.utils

trait Reader[T] {
  def string(t: T): Either[String, String]
  def int(t: T): Either[String, Int]
  def long(t: T): Either[String, Long]
  def bool(t: T): Either[String, Boolean]
  def array(t: T): Either[String, Iterable[T]]
  def obj(t: T): Either[String, Iterable[(String, T)]]
}
