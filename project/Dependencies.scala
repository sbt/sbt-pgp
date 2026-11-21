import sbt.Keys._
import sbt._

object Dependencies {
  val gigahorseOkhttp = Def.setting(
    scalaBinaryVersion.value match {
      case "2.12" =>
        "com.eed3si9n" %% "gigahorse-okhttp" % "0.4.0"
      case _ =>
        "com.eed3si9n" %% "gigahorse-okhttp" % "0.7.0"
    }
  )
  val bouncyCastlePgp = "org.bouncycastle" % "bcpg-jdk18on" % "1.87"
  val specs2 = "org.specs2" %% "specs2-core" % "4.23.0"
  val sbtIo = "org.scala-sbt" %% "io" % "1.13.6"
  val parserCombinators = Def.setting(
    scalaBinaryVersion.value match {
      case "2.12" =>
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
      case _ =>
        "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0"
    }
  )
}
