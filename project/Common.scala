import sbt.Keys._
import sbt._
import com.typesafe.sbt.SbtGit.versionWithGit

/** Common settings for PGP plugin. */
object PgpCommonSettings extends AutoPlugin {

  override def requires = bintray.BintrayPlugin
  override def trigger = allRequirements

  import bintray.BintrayPlugin.autoImport._

  object autoImport {
    // Dependencies
    val gigahorseOkhttp = "com.eed3si9n" %% "gigahorse-okhttp" % "0.3.0"
    val bouncyCastlePgp = "org.bouncycastle" % "bcpg-jdk15on" % "1.51"
    val specs2 = "org.specs2" %% "specs2-core" % "3.8.9"
    val sbtIo  = "org.scala-sbt" %% "io" % "1.0.0-M11"
    val parserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"
    val sbtCoreNext = Def.setting {
      Defaults.sbtPluginExtra("org.scala-sbt" % "sbt-core-next" % "0.1.1", sbtBinaryVersion.value, scalaBinaryVersion.value)
    }
  }

  override def projectSettings =
    Seq(
      organization := "com.jsuereth",
      scalacOptions in Compile := Seq("-feature", "-deprecation", "-Xlint"),
      publishMavenStyle := false,
      bintrayOrganization := Some("sbt"),
      bintrayRepository := "sbt-plugin-releases",
      // Because we're both a library and an sbt plugin, we use crossScalaVersions rather than crossSbtVersions for
      // cross building. So you can use commands like +scripted.
      crossScalaVersions := Seq("2.10.6", "2.12.3"),
      sbtVersion in pluginCrossBuild := {
        scalaBinaryVersion.value match {
          case "2.10" => "0.13.16"
          case "2.12" => "1.1.5"
        }
      }
    )
}
