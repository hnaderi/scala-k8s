package dev.hnaderi.k8s.utils

final case class ObjectWriter[T](fields: List[(String, T)] = Nil)
    extends AnyVal {
  def write[A](key: String, value: A)(implicit
      enc: Encoder[A, T]
  ): ObjectWriter[T] =
    copy(fields = (key, value.encodeTo) :: fields)
  def write[A](key: String, value: Option[A])(implicit
      enc: Encoder[A, T]
  ): ObjectWriter[T] =
    value.fold(this)(newValue =>
      copy(fields = (key, newValue.encodeTo) :: fields)
    )
  def build(implicit builder: Builder[T]): T = builder.obj(fields)
}
