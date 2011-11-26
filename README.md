# PGP Plugin

This plugin aims to provide PGP signing for XSBT (SBT 0.11+ versions).  The plugin currently uses the command line GPG process with the option to use the Bouncy Castle java security library for PGP. 


## Usage

If you already have GPG configured, simply add the following to your `~/.sbt/plugins/project/build.scala` file:

    import sbt._

    object PluginDef extends Build {
      override def projects = Seq(root)
      lazy val root = Project("plugins", file(".")) dependsOn(gpg)
      lazy val gpg = uri("git://github.com/jsuereth/xsbt-gpg-plugin.git#0.3")
    }

The plugin should wire into all your projects and sign files before they are deployed.

### GPG

If you have GPG installed in a non standard location, you can configure it by adding the following to your `~/.sbt/gpg.sbt` file:

    gpgCommand := "/path/to/gpg"

You can configure an alternative keyring using the gpgSecretRing setting:

    gpgSecretRing := file("/path/to/my/secring.gpg")


### Bouncy Castle

If you do not have GPG installed and configured and would like to use bouncy castle for encryption, simply configure the plugin as above and add the following to your `~/.sbt/gpg.sbt` file:

    gpgPassphrase := Some(Array('t','e','s','t'))

The passphrase *must* be an array of characters and should be unique to yourself.   The plugin will choke on an empty passphrase, and it's a poor idea to sign artifacts using a key with no passphrase, so don't do it.

After the passphrase is configured, generate a PGP key in sbt by running the following task:

    > gpg-gen-key your@email.com Your Name Here

This will construct a new key with the identity of "Your Name <your@email.com>".  The public key is placed into the `~/.sbt/gpg/pubring.asc` file by default.   It's a good idea to get this key certified by a trusted agency of your users.

No other configuration is necessary to begin signing artifacts.   This should happen automatically when deploying.
