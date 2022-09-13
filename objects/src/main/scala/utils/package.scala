package dev.hnaderi.k8s

package object utils {
  implicit class EncoderOps[R](val r: R) extends AnyVal {
    def encodeTo[T](implicit enc: Encoder[R, T]): T = enc(r)
  }
}
