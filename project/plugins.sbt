resolvers += Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

//addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.5")

libraryDependencies += Defaults.sbtPluginExtra("com.jsuereth" % "sbt-git-plugin" % "0.4", "0.12.0-Beta2", "2.9.2")

libraryDependencies += Defaults.sbtPluginExtra("com.jsuereth" % "sbt-ghpages-plugin" % "0.2.0", "0.12.0-Beta2", "2.9.2")
