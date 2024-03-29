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

package dev.hnaderi.k8s.client

import apis.appsv1._

trait AppsV1 {
  final val deployments = ClusterDeploymentAPI
  final val daemonsets = ClusterDaemonSetAPI
  final val replicasets = ClusterReplicaSetAPI
  final val statefulsets = ClusterStatefulSetAPI
}

object AppsV1 extends APIGroupAPI("/apis/apps/v1") with AppsV1
