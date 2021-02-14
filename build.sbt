import Dependencies._

val LibraryDoc = config("library-doc")
val PluginDoc = config("plugin-doc")

ThisBuild / organization := "com.github.sbt"
ThisBuild / homepage := Some(url("https://github.com/sbt/sbt-pgp"))
ThisBuild / Compile / scalacOptions := Seq("-feature", "-deprecation", "-Xlint")

// Because we're both a library and an sbt plugin, we use crossScalaVersions rather than crossSbtVersions for
// cross building. So you can use commands like +scripted.
ThisBuild / crossScalaVersions := Seq("2.10.7", "2.12.8")

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
    SiteScaladocPlugin.scaladocSettings(LibraryDoc, mappings in (Compile, packageDoc) in library, "library/latest/api"),
    SiteScaladocPlugin.scaladocSettings(PluginDoc, mappings in (Compile, packageDoc) in plugin, "plugin/latest/api"),
    crossScalaVersions := Vector.empty,
  )

lazy val plugin = (project in file("sbt-pgp"))
  .enablePlugins(SbtPlugin)
  .dependsOn(library)
  .settings(
    name := "sbt-pgp",
    crossSbtVersions := Seq("0.13.18", "1.1.6"),
    libraryDependencies += gigahorseOkhttp,
    libraryDependencies ++= {
      (sbtBinaryVersion in pluginCrossBuild).value match {
        case "0.13" => Defaults.sbtPluginExtra("org.scala-sbt" % "sbt-core-next" % "0.1.1", "0.13", scalaBinaryVersion.value) :: Nil
        case _      => Nil
      }
    },
    publishLocal := publishLocal.dependsOn(publishLocal in library).value,
    scriptedBufferLog := false,
    scriptedLaunchOpts += s"-Dproject.version=${version.value}",
    sbtVersion in pluginCrossBuild := {
      scalaBinaryVersion.value match {
        case "2.10" => "0.13.18"
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
    libraryDependencies ++= Seq(bouncyCastlePgp, gigahorseOkhttp,
      specs2 % Test, sbtIo % Test),
    libraryDependencies ++= {
      scalaBinaryVersion.value match {
        case "2.10" => Nil
        case _      => Seq(parserCombinators)
      }
    }
  )

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/sbt/sbt-pgp"),
    "scm:git@github.com:sbt/sbt-pgp.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "jsuereth",
    name  = "Josh Suereth",
    email = "@jsuereth",
    url   = url("https://github.com/jsuereth")
  ),
  Developer(
    id    = "eed3si9n",
    name  = "Eugene Yokota",
    email = "@eed3si9n",
    url   = url("https://eed3si9n.com/")
  ),
)
ThisBuild / description := "sbt-pgp provides PGP signing for sbt"
ThisBuild / licenses := List("BSD-3-Clause" -> new URL("https://github.com/sbt/sbt-pgp/blob/develop/LICENSE"))
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
