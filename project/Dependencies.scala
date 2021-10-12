import sbt.Keys._
import sbt._

object Dependencies {
  val gigahorseOkhttp = "com.eed3si9n" %% "gigahorse-okhttp" % "0.4.0"
  val bouncyCastlePgp = "org.bouncycastle" % "bcpg-jdk15on" % "1.69"
  val specs2 = "org.specs2" %% "specs2-core" % "3.10.0"
  val sbtIo = "org.scala-sbt" %% "io" % "1.2.2"
  val parserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
}
