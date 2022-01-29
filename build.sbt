import Dependencies._

val LibraryDoc = config("library-doc")
val PluginDoc = config("plugin-doc")

ThisBuild / organization := "com.github.sbt"
ThisBuild / homepage := Some(url("https://github.com/sbt/sbt-pgp"))
ThisBuild / Compile / scalacOptions := Seq("-feature", "-deprecation", "-Xlint")

// Because we're both a library and an sbt plugin, we use crossScalaVersions rather than crossSbtVersions for
// cross building. So you can use commands like +scripted.
lazy val scala212 = "2.12.15"
ThisBuild / crossScalaVersions := Seq(scala212)
ThisBuild / scalaVersion := scala212

ThisBuild / scalafmtOnCompile := true
ThisBuild / dynverSonatypeSnapshots := true
ThisBuild / version := {
  val orig = (ThisBuild / version).value
  if (orig.endsWith("-SNAPSHOT")) "2.0.2-SNAPSHOT"
  else orig
}

lazy val root = (project in file("."))
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(JekyllPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .aggregate(library, plugin)
  .settings(
    name := "sbt-pgp root",
    publish / skip := true,
    git.remoteRepo := "git@github.com:sbt/sbt-pgp.git",
    SiteScaladocPlugin.scaladocSettings(LibraryDoc, library / Compile / packageDoc / mappings, "library/latest/api"),
    SiteScaladocPlugin.scaladocSettings(PluginDoc, plugin / Compile / packageDoc / mappings, "plugin/latest/api"),
    crossScalaVersions := Vector.empty
  )

lazy val plugin = (project in file("sbt-pgp"))
  .enablePlugins(SbtPlugin)
  .dependsOn(library)
  .settings(
    name := "sbt-pgp",
    libraryDependencies += gigahorseOkhttp,
    publishLocal := publishLocal.dependsOn((library / publishLocal)).value,
    scriptedBufferLog := false,
    scriptedLaunchOpts += s"-Dproject.version=${version.value}",
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.1.6"
      }
    }
  )

// library
// The library of PGP functions.
// Note:  We're going to just publish this to the sbt repo now.
lazy val library = (project in file("gpg-library"))
  .settings(
    name := "pgp-library",
    libraryDependencies ++= Seq(bouncyCastlePgp, gigahorseOkhttp, specs2 % Test, sbtIo % Test),
    libraryDependencies ++= Seq(parserCombinators)
  )

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/sbt/sbt-pgp"),
    "scm:git@github.com:sbt/sbt-pgp.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "jsuereth",
    name = "Josh Suereth",
    email = "@jsuereth",
    url = url("https://github.com/jsuereth")
  ),
  Developer(
    id = "eed3si9n",
    name = "Eugene Yokota",
    email = "@eed3si9n",
    url = url("https://eed3si9n.com/")
  )
)
ThisBuild / description := "sbt-pgp provides PGP signing for sbt"
ThisBuild / licenses := List("BSD-3-Clause" -> new URL("https://github.com/sbt/sbt-pgp/blob/develop/LICENSE"))
ThisBuild / pomIncludeRepository := { _ =>
  false
}
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
