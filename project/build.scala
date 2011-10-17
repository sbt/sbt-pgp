import sbt._
import Keys._

object GpgBuild extends Build {
  val plugin = Project("plugin", file(".")) dependsOn(library) settings(
    sbtPlugin := true,
    name := "xsbt-gpg-plugin"
  )
  lazy val library = Project("library", file("gpg-library")) settings(
    name := "xsbt-gpg-plugin",
    crossScalaVersions := Seq("2.9.1", "2.9.0-1", "2.9.0", "2.8.2", "2.8.1", "2.8.0"),
    libraryDependencies += "org.bouncycastle" % "bcpg-jdk16" % "1.46"
  )
}
