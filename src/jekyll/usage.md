---
layout: default
title: PGP plugin Usage
---


By default, the `xsbt-gpg-plugin` will use the [Bouncy Castle](http://www.bouncycastle.org/) library, an implementation of PGP.   It is a java-only solution that gives the plugin great flexibility in what it can do and how it performs it.   It als works well with `gpg` command line utility, and keys generated using it.   By default the PGP plugin will attempt to use your GPG key for PGP encryption.  GPG is the GNU project's PGP implementation.   It provides great support and is available on many platforms.

# Creating a key pair #

To use the GPG comand line tool.  Simply run the command:

    gpg --gen-key

To create a key pair using the SBT plugin (and Bouncy Castle), In the SBT prompt, enter the following:

    > gpg-gen-key your@email.com Your Name Here

This will create a new public/secret key pair for you using the configured passphrase in the default location (`~/.sbt/gpg`).  If you already have a key in place, this will warn you and not generate a new key.

_Note: If you have a GPG key pair (the `~/.gnupg/pubring.gpg` and `~/.gnupg/secring.gpg` files exist), then the GPG plugin will assume you want these and prevent you from generating a new key.  You can override this by configuring your own key locations._

You can record your password in a user-specific setting file:

    set gpgPassphrase := Some(Array('t','e','s','t'))

Or you can enter it on the command line when prompted by SBT:

    Please enter your PGP passphrase> ******

_Note: While some may want the feature to create a key without a passphrase, this plugin will not support that.   The purpose of this plugin is to promote security and authentication.  If you're not using a passphrase on you're key, you're not helping._


## Configuring key-pair locations ##

If you'd like to use a key that isn't in the standard location, you can configure it in your `~/.sbt/gpg.sbt` file:

    gpgSecretRing := file("/tmp/secring.asc")

    gpgPublicRing := file("/tmp/pubring.asc")


## Configuring for using GPG ##

The first step towards using the GPG command line tool is to configure the plugin to use it.

    useGpg := true

The gpg plugin needs to know where the `gpg` executable is to run.  On linux/mac it will look for a `gpg` executable on the path and in windows it will look for a `gpg.exe` executable on the path.   To configure a different location, place the following in your `~/.sbt/gpg.sbt` file:

    gpgCommand := "/path/to/gpg"

By default, the gpg plugin will use the default private keys from the standard gpg keyrings.   If you'd like to use a different private key for signing artifacts, add the following to your `~/.sbt/gpg.sbt` file:

    gpgSecretRing := file("/path/to/my/secring.gpg")

There is currently no way to choose a non-default key from the keyring.

## Avoiding the password fiasco ##

The PGP plugin will ask for your password once, for every task that requires the use of the PGP signatures.   The prompt will look something like this:

    Please enter your PGP passphrase> ***********

If you would like to avoid entering your password over and over, you can configure it with a setting:

    gpgPassphrase := Some(Array('M', 'y', 'P', 'a', 's', 's', 'p', 'h', 'r', 'a', 's', 'e'))

_Note: The passphrase *must* be an array of characters.   It's midly more secure.  We hope to provide even more secure ways of storing passphrases in the future._

Also make sure that the above setting is in a user-specific directory and that you don't advertise your password in the source code repository!

IF you are using the GPG command line tool, then the plugin supports the use of the `gpg-agent`.   You can enable its usage with the following setting:

    useGpgAgent := true

## Validating PGP Keys ##

The plugin can be used to validate the PGP signatures of the dependencies of the project you're using.   To validate these signatures, simply use the `check-pgp-signatures` task:

    > check-pgp-signatures
    [info] Resolving org.scala-lang#scala-library;2.9.1 ...
    ...
    [info] ----- PGP Signature Results -----
    [info]                    com.novocode : junit-interface :        0.7 : jar   [MISSING]
    [info]               javax.transaction :             jta :     1.0.1B : jar   [MISSING]
    [info]          org.scala-lang.plugins :   continuations :      2.9.1 : jar   [MISSING]
    [info]                org.apache.derby :           derby : 10.5.3.0_1 : jar   [UNTRUSTED(0x98e21827)]
    [error] {file:/home/josh/projects/typesafe/test-signing/}test-gpg/*:check-pgp-signatures: Some artifacts have bad signatures or are signed by untrusted sources!
    [error] Total time: 2 s, completed Jan 23, 2012 12:03:28 PM
    
In the above otuput, the signature for derby is from an untrusted key (id: `0x98e21827`).  You can import this key into your public key ring, and then the plugin will trust artifacts from that key.   The public, by default, accepts any keys included in your public key ring file.


## Configuring a public key ring ##

You can configure the public key ring you use with the `gpgPublicRing` setting.

    gpgPublicRing := file("/home/me/pgp/pubring.asc")

*By default the `~/.gnupg/pubring.gpg` file is used, if it exists.*


## Importing keys from public key servers ##

TODO

## Export your public key ##

Using the gpg command line, run the following:

    gpg --keyserver hkp://keyserver.ubuntu.com --send-keys <your key id>


Not yet implemented for direct plugin usage.


