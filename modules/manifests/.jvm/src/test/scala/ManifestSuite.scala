/*
 * Copyright 2022 Hossein Naderi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.hnaderi.k8s.test

import dev.hnaderi.k8s.manifest._
import munit.FunSuite

//
// NOTE These tests are placed here in order to keep project setup easier.
//
class ManifestSuite extends FunSuite {
  private val singleDoc = """
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    app.kubernetes.io/version: 1.0.0
    app.kubernetes.io/managed-by: dev.hnaderi.sbtk8s
  name: example-config
data:
  app.conf: "data"
"""

  private val multiDoc = """
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    app.kubernetes.io/version: 1.0.0
    app.kubernetes.io/managed-by: dev.hnaderi.sbtk8s
  name: example-config
data:
  app.conf: "data"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app.kubernetes.io/name: example
    app.kubernetes.io/version: 1.0.0
    app.kubernetes.io/managed-by: dev.hnaderi.sbtk8s
  name: example
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: example
  template:
    spec:
      containers:
      - image: hello-world
        resources:
          requests:
            cpu: 250m
            memory: 128Mi
          limits:
            cpu: 500m
            memory: 512Mi
        name: example
    metadata:
      labels:
        app.kubernetes.io/name: example
        app.kubernetes.io/version: 1.0.0
        app.kubernetes.io/managed-by: dev.hnaderi.sbtk8s
      name: example
"""

  test("single doc") {
    val result = parseObj(singleDoc)

    assert(result.isRight)
  }

  test("multi docs") {
    val result = parseAllObjects(multiDoc)

    assert(result.isRight)
  }

}
