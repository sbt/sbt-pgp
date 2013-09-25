---
layout: default
title: SBT PGP Plugin - Overview
---

The `sbt-pgp` plugin provides PGP signing for SBT 0.12+.  Some OSS repositories (e.g. Sonatype) will require that you sign artifacts with publicly available keys prior to release.  These are two of the services that `sbt-pgp` provides.

## Installation ##

Add the following to your `~/.sbt/plugins/gpg.sbt` file:
   
```
   addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.1")
```

## Usage

There are two modes of use:

* By default, the `sbt-pgp` plugin will use the [Bouncy Castle](http://www.bouncycastle.org/) library, an implementation of PGP that is included with the plugin.  It is a Java-only solution that gives the plugin great flexibility in what it can do and how it performs it.

* The `gpg` command-line utility which is GNU's PGP implementation.  It provides great support and is available on many platforms.  You'll need to make sure this is installed prior to usage as this dependency is not provided.

[Detailed usage and configuration instructions](usage.html).

To publish signed artifacts, you must use the new `publish-signed` and `publish-local-signed` tasks.  `sbt-pgp` no longer wires in to the default `publish` and `publish-local` tasks of SBT.
