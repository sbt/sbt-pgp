addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.6.2")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

libraryDependencies += "net.databinder" %% "dispatch" % "0.8.10"

