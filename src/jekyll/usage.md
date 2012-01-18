---
layout: default
title: PGP plugin Usage
---


By default, the `xsbt-gpg-plugin` will use the `gpg` command line utility.   GPG is the GNU project's PGP implementation.   It provides great support and is available on many platforms.   The `xsbt-gpg-plugin` also supports [Bouncy Castle](http://www.bouncycastle.org/) a pure Java implementation of PGP.

# Creating a key pair #

To use the GPG comand line tool.  Simply run the command:

    gpg --gen-key

To create a key pair using the SBT plugin (and Bouncy Castle), you must first configure SBT so it knows your secret passphrase.  Place your passphrase configuration into your `~/.sbt/gpg.sbt` file
    
    gpgPassphrase := Some(Array('t','e','s','t'))

or you can temporarily enter your password using SBT's `set` command:

    set gpgPassphrase := Some(Array('t','e','s','t'))

_Note: While some may want the feature to create a key without a passphrase, this plugin will not support that.   The purpose of this plugin is to promote security and authentication.  If you're not using a passphrase on you're key, you're not helping._

Next, start sbt (if not already started).   In the prompt, enter the following:

    > gpg-gen-key your@email.com Your Name Here

This will create a new public/secret key pair for you using the configured passphrase in the default location (`~/.sbt/gpg`).  If you already have a key in place, this will warn you and not generate a new key.

_Note: If you have a GPG key pair (the `~/.gnupg/pubring.gpg` and `~/.gnupg/secring.gpg` files exist), then the GPG plugin will assume you want these and prevent you from generating a new key.  You can override this by configuring your own key locations._

## Configuring key-pair locations ##

If you'd like to use a key that isn't in the standard location, you can configure it in your `~/.sbt/gpg.sbt` file:

    gpgSecretRing := file("/tmp/secring.asc")

    gpgPublicRing := file("/tmp/pubring.asc")


## Configuring for using GPG ##

The gpg plugin needs to know where the `gpg` executable is to run.  On linux/mac it will look for a `gpg` executable on the path and in windows it will look for a `gpg.exe` executable on the path.   To configure a different location, place the following in your `~/.sbt/gpg.sbt` file:

    gpgCommand := "/path/to/gpg"

By default, the gpg plugin will use the default private keys from the standard gpg keyrings.   If you'd like to use a different private key for signing artifacts, add the following to your `~/.sbt/gpg.sbt` file:

    gpgSecretRing := file("/path/to/my/secring.gpg")

There is currently no way to choose a non-default key from the keyring.

## Avoiding the password fiasco ##

The gpg plugin assumes the use of the `gpg-agent` daemon for entering the pasphrase.  Consult your OSes documentation on using the gpg-agent.   If you do not want to install the gpg-agent, you can configure your passphrase in your `~/.sbt/gpg.sbt` file:

    gpgPassphrase := Some(Array('M', 'y', 'P', 'a', 's', 's', 'p', 'h', 'r', 'a', 's', 'e'))

_Note: The passphrase *must* be an array of characters.   It's midly more secure.  We hope to provide even more secure ways of storing passphrases in the future._

If the passphrase is configured for the gpg plugin, then it will use the Bouncy Castle java library rather than the `gpg` command line tool.
