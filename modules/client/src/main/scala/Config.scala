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

package dev.hnaderi.k8s
package client

import dev.hnaderi.k8s.utils._

final case class Config(
    apiVersion: String,
    clusters: Seq[NamedCluster],
    contexts: Seq[NamedContext],
    `current-context`: String,
    users: Seq[NamedAuthInfo]
)

object Config {
  implicit val decoder: Decoder[Config] = new Decoder[Config] {
    override def apply[T: Reader](t: T): Either[String, Config] = for {
      obj <- ObjectReader(t)
      version <- obj.read[String]("apiVersion")
      cls <- obj.read[Seq[NamedCluster]]("clusters")
      ctxs <- obj.read[Seq[NamedContext]]("contexts")
      curCtx <- obj.getString("current-context")
      users <- obj.read[Seq[NamedAuthInfo]]("users")
    } yield Config(
      apiVersion = version,
      clusters = cls,
      contexts = ctxs,
      `current-context` = curCtx,
      users = users
    )
  }
}

final case class NamedCluster(name: String, cluster: Cluster)
object NamedCluster {
  implicit val decoder: Decoder[NamedCluster] = new Decoder[NamedCluster] {
    override def apply[T: Reader](t: T): Either[String, NamedCluster] = for {
      obj <- ObjectReader(t)
      name <- obj.getString("name")
      cluster <- obj.read[Cluster]("cluster")
    } yield NamedCluster(name, cluster)
  }
}
final case class Cluster(
    server: String,
    `certificate-authority`: Option[String] = None,
    `certificate-authority-data`: Option[String] = None
)

object Cluster {
  implicit val decoder: Decoder[Cluster] = new Decoder[Cluster] {
    override def apply[T: Reader](t: T): Either[String, Cluster] = for {
      obj <- ObjectReader(t)
      server <- obj.getString("server")
      ca <- obj.readOpt[String]("certificate-authority")
      caData <- obj.readOpt[String]("certificate-authority-data")
    } yield Cluster(
      server,
      `certificate-authority` = ca,
      `certificate-authority-data` = caData
    )
  }
}

final case class NamedContext(name: String, context: Context)

object NamedContext {
  implicit val decoder: Decoder[NamedContext] = new Decoder[NamedContext] {
    override def apply[T: Reader](t: T): Either[String, NamedContext] = for {
      obj <- ObjectReader(t)
      name <- obj.getString("name")
      ctx <- obj.read[Context]("context")
    } yield NamedContext(name, ctx)
  }
}
final case class Context(
    cluster: String,
    user: String,
    namespace: Option[String] = None
)

object Context {
  implicit val decoder: Decoder[Context] = new Decoder[Context] {
    override def apply[T: Reader](t: T): Either[String, Context] = for {
      obj <- ObjectReader(t)
      cluster <- obj.getString("cluster")
      user <- obj.getString("user")
      ns <- obj.readOpt[String]("namespace")
    } yield Context(cluster = cluster, user = user, namespace = ns)
  }
}

final case class NamedAuthInfo(name: String, user: AuthInfo)

object NamedAuthInfo {
  implicit val decoder: Decoder[NamedAuthInfo] = new Decoder[NamedAuthInfo] {
    override def apply[T: Reader](t: T): Either[String, NamedAuthInfo] = for {
      obj <- ObjectReader(t)
      name <- obj.getString("name")
      user <- obj.read[AuthInfo]("user")
    } yield NamedAuthInfo(name, user)
  }
}
final case class AuthInfo(
    `client-certificate`: Option[String] = None,
    `client-certificate-data`: Option[String] = None,
    `client-key`: Option[String] = None,
    `client-key-data`: Option[String] = None,
    token: Option[String] = None,
    username: Option[String] = None,
    password: Option[String] = None
)

object AuthInfo {
  implicit val decoder: Decoder[AuthInfo] = new Decoder[AuthInfo] {
    override def apply[T: Reader](t: T): Either[String, AuthInfo] = for {
      obj <- ObjectReader(t)
      cert <- obj.readOpt[String]("client-certificate")
      certData <- obj.readOpt[String]("client-certificate-data")
      key <- obj.readOpt[String]("client-key")
      keyData <- obj.readOpt[String]("client-key-data")
      token <- obj.readOpt[String]("token")
      username <- obj.readOpt[String]("username")
      password <- obj.readOpt[String]("password")
    } yield AuthInfo(
      `client-certificate` = cert,
      `client-certificate-data` = certData,
      `client-key` = key,
      `client-key-data` = keyData,
      token = token,
      username = username,
      password = password
    )
  }
}
