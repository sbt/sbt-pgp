
## Developing

This plugin uses ordinary Scala cross building to cross build against different sbt versions, not the built in sbt cross building, since that only works for projects that are just sbt plugins, it doesn't work for projects that have ordinary Scala libraries too.

So, to cross build this project you can run `+publishLocal` or `+scripted` etc.
