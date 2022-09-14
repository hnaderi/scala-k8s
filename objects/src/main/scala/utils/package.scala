package dev.hnaderi.k8s

package object utils {
  implicit class EncoderOps[R](val r: R) extends AnyVal {
    def encodeTo[T](implicit enc: Encoder[R, T]): T = enc(r)
  }
  implicit class DecoderOps[T](val t: T) extends AnyVal {
    def decodeTo[R](implicit dec: Decoder[T, R]): Either[String, R] = dec(t)
  }
}
