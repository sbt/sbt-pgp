resolvers += Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

resolvers += "jgit-releases" at "http://download.eclipse.org/jgit/maven"

// This should never be in a project build, but global config.
//addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.0")


// We need to be able to cross-build, ideally using SBT 0.12 to publish our SBT 0.13 artifacts.
addSbtPlugin("net.virtual-void" % "sbt-cross-building" % "0.7.0-RC2")
