package dev.hnaderi.k8s

package object generator {
  type CodeGeneratorFor[T <: DataModel] = T => String
  type CodeGenerator = CodeGeneratorFor[DataModel]
}
