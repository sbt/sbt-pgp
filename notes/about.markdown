# sbt-pgp #

The sbt-pgp is a PGP security resource for SBT projects.   The gpg plugin currently allows projects to:

* [Create PGP keys](http://github.com/sbt/sbt-pgp/wiki/Using-Bouncy-Castle)
* Sign artifacts when publishing

The GPG plugin can either shell out to the `gpg` command line tool, or use a Pure Java PGP implementation to generate PGP keyrings inside SBT and sign artifacts.

See the [Wiki](http://github.com/sbt/sbt-pgp/wiki/) for configuration information.
