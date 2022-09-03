package dev.hnaderi.k8s.generator

import sbt._
import sbt.Keys._

trait Keys {
  val k8sManagedTarget: SettingKey[File] = settingKey(
    "Source codes that are managed by plugin"
  )
  val k8sUnmanagedTarget: SettingKey[File] = settingKey(
    "Source codes that are not managed by plugin"
  )
}

object Keys extends Keys
