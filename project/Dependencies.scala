import sbt.Keys._
import sbt._

object Dependencies {
  val gigahorseOkhttp = "com.eed3si9n" %% "gigahorse-okhttp" % "0.3.0"
  val bouncyCastlePgp = "org.bouncycastle" % "bcpg-jdk15on" % "1.60"
  val specs2 = "org.specs2" %% "specs2-core" % "3.8.9"
  val sbtIo  = "org.scala-sbt" %% "io" % "1.0.0-M11"
  val parserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"
  val sbtCoreNext = Def.setting {
    Defaults.sbtPluginExtra("org.scala-sbt" % "sbt-core-next" % "0.1.1", sbtBinaryVersion.value, scalaBinaryVersion.value)
  }
}
