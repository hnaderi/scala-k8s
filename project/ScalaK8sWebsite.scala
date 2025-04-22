import cats.data.NonEmptyList
import laika.ast.Path.Root
import laika.config.ApiLinks
import laika.config.LaikaKeys
import laika.config.LinkConfig
import laika.config.SourceLinks
import laika.config.SyntaxHighlighting
import laika.format.Markdown
import laika.helium.config.*
import laika.sbt.LaikaConfig
import laika.sbt.LaikaPlugin.autoImport.*
import org.typelevel.sbt.TypelevelSitePlugin
import sbt.*
import sbt.Keys.*

import TypelevelSitePlugin.autoImport.*

object ScalaK8sWebsite extends AutoPlugin {
  override def requires: Plugins = TypelevelSitePlugin

  private val relatedProjectLinks = NonEmptyList
    .of(
      "Kubernetes" -> url("https://github.com/kubernetes/kubernetes"),
      "sbt k8s" -> url("https://github.com/hnaderi/sbt-k8s"),
      TypelevelProject.Http4s,
      TypelevelProject.Fs2,
      TypelevelProject.Scalacheck,
      "ZIO" -> url("https://github.com/zio/zio"),
      "ZIO-http" -> url("https://github.com/zio/zio-http"),
      "ZIO-json" -> url("https://github.com/zio/zio-json"),
      "sttp" -> url("https://sttp.softwaremill.com"),
      "Circe" -> url("https://github.com/circe/circe"),
      "Spray json" -> url("https://github.com/spray/spray-json"),
      "Play json" -> url("https://github.com/playframework/play-json"),
      "Json4s" -> url("https://github.com/json4s/json4s"),
      "Jawn" -> url("https://github.com/typelevel/jawn")
    )
    .map { case (title, url) => TextLink.external(url.toString, title) }

  override def projectSettings: Seq[Setting[_]] = Seq(
    tlSiteHelium ~= {
      _.site
        .topNavigationBar(
          homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home)
        )
        .site
        .mainNavigation(appendLinks =
          Seq(
            ThemeNavigationSection(
              "Related projects",
              relatedProjectLinks.head,
              relatedProjectLinks.tail: _*
            )
          )
        )
    },
    laikaConfig := {
      val apiDoc = tlSiteApiUrl.value.toSeq
        .map(_.toString())
        .map(ApiLinks(_).withPackagePrefix("dev.hnaderi.k8s"))

      LaikaConfig.defaults.withConfigValue(
        LinkConfig.empty.addApiLinks(apiDoc: _*)
      )
    }
  )

}
