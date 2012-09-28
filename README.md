# PGP Plugin

This plugin aims to provide PGP signing for XSBT (SBT 0.11+ versions).  The plugin currently uses the command line GPG process with the option to use the Bouncy Castle java security library for PGP. 


## Usage

If you already have GPG configured, simply add the following to your `~/.sbt/plugins/gpg.sbt` file:

    resolvers += Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
    
    addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.7")

The plugin should wire into all your projects and sign files before they are deployed.

No other configuration should be necessary if you have a `gpg` generated key available.

Please see the [documentation](http://scala-sbt.org/sbt-pgp) for more information on usage.
