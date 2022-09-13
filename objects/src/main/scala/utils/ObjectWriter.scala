package dev.hnaderi.k8s.utils

final case class ObjectWriter[T](private val fields: List[(String, T)] = Nil)(
    implicit builder: Builder[T]
) {
  def write(key: String, value: T): ObjectWriter[T] =
    copy(fields = (key, value) :: fields)
  def write(key: String, value: Option[T]): ObjectWriter[T] =
    value.fold(this)(newValue => copy(fields = (key, newValue) :: fields))
  def build: T = builder.obj(fields)
}
