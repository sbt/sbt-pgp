import sbt._
import Keys._

object GpgBuild extends Build {
  val defaultSettings: Seq[Setting[_]] = Seq(
    organization := "com.jsuereth",
    version := "0.5",
    publishTo <<= (version) { v =>
      import Classpaths._
      Option(if(v endsWith "SNAPSHOT") typesafeSnapshots else typesafeResolver)
    },
    publishMavenStyle := false,
    publishTo := Some(Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))
  )

  val plugin = Project("plugin", file(".")) dependsOn(library) settings(defaultSettings:_*) settings(
    sbtPlugin := true,
    name := "xsbt-gpg-plugin"
  ) 
  lazy val library = Project("library", file("gpg-library")) settings(defaultSettings:_*) settings(
    name := "gpg-library",
    crossScalaVersions := Seq("2.9.1", "2.9.0-1", "2.9.0", "2.8.2", "2.8.1", "2.8.0"),
    libraryDependencies += "org.bouncycastle" % "bcpg-jdk16" % "1.46"
  )
}
