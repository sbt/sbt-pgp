---
layout: default
title: SBT PGP plugin.
---

This plugin aims to provide PGP signing for XSBT (SBT 0.11+ versions).  The plugin currently uses the command line GPG process with the option to use the Bouncy Castle java security library for PGP. 

## Usage ##

*Please see [here for advanced usage](usage.html).*

If you already have GPG configured, simply add the following to your `~/.sbt/plugins/project/build.scala` file:

    resolvers += Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
    
    addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.5")

The plugin should wire into all your projects and sign files before they are deployed.  See [Usage](usage.html) for more information.

