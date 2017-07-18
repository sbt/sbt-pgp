import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.SbtSite.site
import sbt.ScriptedPlugin._

versionWithGit

// library
// The library of PGP functions.
// Note:  We're going to just publish this to the sbt repo now.
lazy val library =
  Project("library", file("gpg-library"))
    .settings(
      name := "pgp-library",
      // scalaVersion := "2.12.2",
      libraryDependencies ++= Seq(bouncyCastlePgp, gigahorseOkhttp,
        specs2 % Test, sbtIo % Test),
      libraryDependencies ++= {
        scalaBinaryVersion.value match {
          case "2.10" => Nil
          case _      => Seq(parserCombinators)
        }
      }
    )

// The sbt plugin.
lazy val plugin =
  Project("plugin", file("pgp-plugin"))
    .dependsOn(library)
    .settings(
      sbtPlugin := true,
      name := "sbt-pgp",
      // scalaVersion := "2.12.2",
      libraryDependencies += gigahorseOkhttp,
      libraryDependencies ++= {
        (sbtBinaryVersion in pluginCrossBuild).value match {
          case "0.13" => Seq(sbtCoreNext.value)
          case _      => Nil
        }
      },
      publishLocal := publishLocal.dependsOn(publishLocal in library).value
    )
    // .settings(websiteSettings:_*)
    .settings(scriptedSettings1:_*)
    .settings(
      scriptedLaunchOpts += s"-Dproject.version=${version.value}"
    )

// Website settings

site.settings

ghpages.settings

site.jekyllSupport()

site.includeScaladoc()


git.remoteRepo := "git@github.com:sbt/sbt-pgp.git"

site.addMappingsToSiteDir(mappings in packageDoc in Compile in library, "library/latest/api")

site.addMappingsToSiteDir(mappings in packageDoc in Compile in plugin, "plugin/latest/api")

// Release settings
Release.settings

// Disable publishing of root
publish := ()

publishLocal := ()

// WORKAROUND https://github.com/sbt/sbt/issues/3325
def scriptedSettings1 = Def settings (
  ScriptedPlugin.scriptedSettings filterNot (_.key.key.label == libraryDependencies.key.label),
  libraryDependencies ++= {
    val cross = CrossVersion.partialVersion(scriptedSbt.value) match {
      case Some((0, 13)) => CrossVersion.Disabled
      case Some((1, _))  => CrossVersion.binary
      case _             => sys error s"Unhandled sbt version ${scriptedSbt.value}"
    }
    Seq(
      "org.scala-sbt" % "scripted-sbt" % scriptedSbt.value % scriptedConf.toString cross cross,
      "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
    )
  }
)
