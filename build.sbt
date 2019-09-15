import Dependencies._

val LibraryDoc = config("library-doc")
val PluginDoc = config("plugin-doc")

lazy val root = (project in file("."))
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(JekyllPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(GitVersioning)
  .aggregate(library, plugin)
  .settings(
    name := "sbt-pgp root",
    publish / skip := true,
    git.remoteRepo := "git@github.com:sbt/sbt-pgp.git",
    SiteScaladocPlugin.scaladocSettings(LibraryDoc, mappings in (Compile, packageDoc) in library, "library/latest/api"),
    SiteScaladocPlugin.scaladocSettings(PluginDoc, mappings in (Compile, packageDoc) in plugin, "plugin/latest/api"),
    // Release settings
    Release.settings,
    crossScalaVersions := Vector.empty,
  )

lazy val plugin = (project in file("sbt-pgp"))
  .enablePlugins(SbtPlugin)
  .dependsOn(library)
  .settings(
    commonSettings,
    name := "sbt-pgp",
    crossSbtVersions := Seq("0.13.17", "1.1.6"),
    libraryDependencies += gigahorseOkhttp,
    libraryDependencies ++= {
      (sbtBinaryVersion in pluginCrossBuild).value match {
        case "0.13" => Seq(sbtCoreNext.value)
        case _      => Nil
      }
    },
    publishLocal := publishLocal.dependsOn(publishLocal in library).value,
    scriptedLaunchOpts += s"-Dproject.version=${version.value}"
  )

// library
// The library of PGP functions.
// Note:  We're going to just publish this to the sbt repo now.
lazy val library = (project in file("gpg-library"))
  .settings(
    commonSettings,
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

lazy val commonSettings = Seq(
  organization := "com.jsuereth",
  Compile / scalacOptions := Seq("-feature", "-deprecation", "-Xlint"),
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
