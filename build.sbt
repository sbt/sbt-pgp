organization := "com.jsuereth"

version := "0.3-SNAPSHOT"

libraryDependencies += "org.bouncycastle" % "bcpg-jdk16" % "1.46"

publishTo <<= (version) { v =>
  import Classpaths._
  Option(if(v endsWith "SNAPSHOT") typesafeSnapshots else typesafeResolver)
}

publishMavenStyle := false
