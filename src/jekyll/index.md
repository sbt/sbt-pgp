---
layout: default
title: SBT PGP plugin.
---

This plugin aims to provide PGP signing for XSBT (SBT 0.12+ versions).  The plugin currently uses the command line GPG process with the option to use the Bouncy Castle java security library for PGP. 


**WARNING** The PGP plugin *NO LONGER* signs artifacts using the `publish` and `publish-local` task.  To sign artifacts, please use `publish-signed` and `publish-local-signed` tasks.

## Usage ##

*Please see [here for advanced usage](usage.html).*

If you already have GPG configured, simply add the following to your `~/.sbt/plugins/gpg.sbt` file:
   
    addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8")

The plugin should wire into all your projects and sign files before they are deployed.  Make sure to use the `publish-signed` task rather than the normal `publish` task.

See [Usage](usage.html) for more information.

