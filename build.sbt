import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.SbtSite.site
import sbt.ScriptedPlugin._

versionWithGit

// library
// The library of PGP functions.
// Note:  We're going to just publish this to the sbt repo now.
lazy val library =
  Project("library", file("gpg-library")).settings(
    name := "pgp-library",
    libraryDependencies ++= Seq(bouncyCastlePgp, dispatchDependency, specs2 % "test", sbtIo % "test")
  )

// The sbt plugin.
lazy val plugin =
  Project("plugin", file("pgp-plugin")).
    dependsOn(library).
    settings(
      sbtPlugin := true,
      name := "sbt-pgp",
      libraryDependencies += dispatchDependency,
      publishLocal <<= publishLocal.dependsOn(publishLocal in library)
    ).
    //settings(websiteSettings:_*).
    settings(scriptedSettings:_*).
    settings(
      scriptedLaunchOpts += s"-Dproject.version=${version.value}"
    )

// Website settings

site.settings

ghpages.settings

site.jekyllSupport()

site.includeScaladoc()


git.remoteRepo := "git@github.com:sbt/sbt-pgp.git"

site.addMappingsToSiteDir(mappings in packageDoc in Compile in library, "library/latest/api")

site.addMappingsToSiteDir(mappings in packageDoc in Compile in plugin, "plugin/latest/api")

// Release settings
Release.settings

// Disable publishing of root
publish := ()

publishLocal := ()