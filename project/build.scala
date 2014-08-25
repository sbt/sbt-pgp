import sbt._
import Keys._

import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit._

object SbtPgpBuild extends Build {

  override val settings: Seq[Setting[_]] =
    super.settings ++ versionWithGit

  // Website publishing settings.
  def websiteSettings: Seq[Setting[_]] = (
    site.settings ++ 
    ghpages.settings ++ 
    site.jekyllSupport() ++ 
    site.includeScaladoc() ++ 
    Seq(
      git.remoteRepo := "git@github.com:sbt/sbt-pgp.git",
      site.addMappingsToSiteDir(mappings in packageDoc in Compile in library, "library/latest/api")
    )
  )

  // Common Settings
  val commonSettings: Seq[Setting[_]] =
    Seq(
      organization := "com.typesafe.sbt",
      publishMavenStyle := false
    ) ++ Bintray.settings

  // Dependencies
  val dispatchDependency = "net.databinder" %% "dispatch-http" % "0.8.10"
  val bouncyCastlePgp = "org.bouncycastle" % "bcpg-jdk15on" % "1.49"
  val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  val sbtIo  = "org.scala-sbt" % "io" % "0.13.5-RC1"


  // Root project.  Just makes website and aggregates others.
  val root = (
    Project("sbt-pgp", file(".")) 
    aggregate(plugin, library)
    settings(websiteSettings:_*)
    // Until this plugin is fixed for 0.13.5, we can't do this
    // disablePlugins(plugins.IvyPlugin)
    settings(
      publish := (),
      publishLocal := ()
    )
    settings(Release.settings:_*)
  )

  import sbt.ScriptedPlugin._

  // The sbt plugin.
  lazy val plugin = Project("plugin", file("pgp-plugin")) dependsOn(library) settings(commonSettings:_*) settings(
    sbtPlugin := true,
    name := "sbt-pgp",
    libraryDependencies += dispatchDependency
  ) settings(websiteSettings:_*) settings(scriptedSettings:_*) settings(
    scriptedLaunchOpts += s"-Dproject.version=${version.value}"
  )

  // The library of PGP functions.
  // Note:  We're going to just publish this to the sbt repo now.
  lazy val library = Project("library", file("gpg-library")) settings(commonSettings:_*) settings(
    name := "pgp-library",
    libraryDependencies ++= Seq(bouncyCastlePgp, dispatchDependency, specs2 % "test", sbtIo % "test")
  )
}
