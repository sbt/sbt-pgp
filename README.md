# SBT-PGP Plugin

[![Join the chat at https://gitter.im/sbt/sbt-pgp](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/sbt/sbt-pgp?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This plugin aims to provide PGP signing for versions of SBT >= 0.12.

Please see the [documentation](http://www.scala-sbt.org/sbt-pgp) for more information.

## Developing

This plugin uses ordinary Scala cross building to cross build against different sbt versions, not the built in sbt cross building, since that only works for projects that are just sbt plugins, it doesn't work for projects that have ordinary Scala libraries too.

So, to cross build this project you can run `+publishLocal` or `+scripted` etc.
