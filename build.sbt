organization := "com.jsuereth"

version := "0.3"

libraryDependencies += "org.bouncycastle" % "bcpg-jdk16" % "1.46"

publishTo <<= (version) { v =>
  import Classpaths._
  Option(if(v endsWith "SNAPSHOT") typesafeSnapshots else typesafeResolver)
}

publishMavenStyle := false

publishTo := Some(Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))
