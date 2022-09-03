package dev.hnaderi.k8s.generator

import DataModel.{Resource, SubResource, MetaResource, Primitive}

object ObjectWriter {
  def apply(
      scg: SourceCodeGenerator
  )(generator: CodeGenerator)(data: DataModel) = data match {
    case p: Primitive =>
      scg.unmanaged(pkg = p.pkg, name = p.name).write(generator(p))
    case other =>
      scg.managed(pkg = other.pkg, name = other.name).write(generator(other))
  }
}
