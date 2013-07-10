---
layout: default
title: SBT PGP Plugin
---

This plugin provides PGP signing for SBT (0.12+ versions).  Some OSS repositories (e.g. Sonatype) will require that you sign artifacts with publicly available keys prior to release, a service that `sbt-pgp` provides.

## Installation ##

Add the following to your `~/.sbt/plugins/gpg.sbt` file:
   
```
   addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8")
```

## Usage ##

There are two modes of use, depending on your PGP provider:

* [Usage Instructions](builtin.md) - The Bouncy Castle Java PGP library, a dependency automatically provided with `sbt-pgp`.

* [Usage Instructions](gpg.md) - The `gpg` command-line utility: GNU's PGP implementation.  Note that you'll need to make sure this is installed prior to usage as this dependency is not provided.

`sbt-pgp` will wire into all of your projects and sign files before they are deployed.

**Make sure to use the `publish-signed` or `publish-local-signed` tasks rather than the normal `publish` or `publish-local` tasks.**
