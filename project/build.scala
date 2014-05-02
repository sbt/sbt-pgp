import sbt._
import Keys._

import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit._

object SbtPgpBuild extends Build {

  override val settings: Seq[Setting[_]] =
    super.settings ++ versionWithGit

  // Sonatype Publishing gunk
  def sonatypePublishSettings: Seq[Setting[_]] = Seq(
    // If we want on maven central, we need to be in maven style.
    publishMavenStyle := true,
    publishArtifact in Test := false,
    // The Nexus repo we're publishing to.
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots") 
      else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    // Maven central cannot allow other repos.  We're ok here because the artifacts we
    // we use externally are *optional* dependencies.
    pomIncludeRepository := { x => false },
    licenses += ("BSD" -> url("http://www.opensource.org/licenses/bsd-license.php")),
    homepage := Some(url("http://scala-sbt.org/sbt-pgp/")),
    scmInfo := Some(ScmInfo(url("http://github.com/sbt/sbt-pgp/"),"git://github.com/sbt/sbt-pgp.git")),
    // Maven central wants some extra metadata to keep things 'clean'.
    pomExtra := (
      <developers>
        <developer>
          <id>jsuereth</id>
          <name>Josh Suereth</name>
        </developer>
      </developers>)
  )

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
    organization := "com.jsuereth",
    publishMavenStyle := false,
    publishTo <<= (version) { version: String =>
       val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
       val (name, u) = if (version.contains("-SNAPSHOT")) ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
                       else ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
       Some(Resolver.url(name, url(u))(Resolver.ivyStylePatterns))
    }
  )

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
    settings(
      publishLocal := (),
      publish := ()
    )
  )

  // The sbt plugin.
  lazy val plugin = Project("plugin", file("pgp-plugin")) dependsOn(library) settings(commonSettings:_*) settings(
    sbtPlugin := true,
    organization := "com.typesafe.sbt",
    name := "sbt-pgp",
    libraryDependencies += dispatchDependency
  ) settings(websiteSettings:_*)

  // The library of PGP functions.
  // Note:  We're going to just publish this to the sbt repo now.
  lazy val library = Project("library", file("gpg-library")) settings(commonSettings:_*) settings(
    name := "gpg-library",
    libraryDependencies ++= Seq(bouncyCastlePgp, dispatchDependency, specs2 % "test", sbtIo % "test")
  )
}
