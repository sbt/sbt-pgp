---
layout: default
title: SBT PGP Plugin
---

This plugin provides PGP signing for SBT (0.12+ versions).  Some OSS repositories (e.g. Sonatype) will require that you sign artifacts with publicly available keys prior to release, a service that `sbt-pgp` provides.

## Installation ##

Add the following to your `~/.sbt/plugins/gpg.sbt` file:
   
    addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8")

The plugin should wire into all your projects and sign files before they are deployed.  Make sure to use the `publish-signed` task rather than the normal `publish` task.

## Usage ##

There are two modes of use:

* (Default) The [Bouncy Castle](http://www.bouncycastle.org/) Java PGP library, a dependency automatically provided with `sbt-pgp`.
* The `gpg` command-line utility: GNU's PGP implementation.  Note that you'll need to make sure this is installed prior to usage as this dependency is not provided.

[Detailed usage instructions](usage.html).

## Changes From Previous Versions ##

Note that the `sbt-pgp` plugin *NO LONGER* signs artifacts using the `publish` and `publish-local` tasks.  To sign artifacts, please use the `publish-signed` and `publish-local-signed` tasks instead.

