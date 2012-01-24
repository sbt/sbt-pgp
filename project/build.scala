import sbt._
import Keys._

import com.jsuereth.sbtsite.SitePlugin.site
import com.jsuereth.sbtsite.SiteKeys._
import com.jsuereth.ghpages.GhPages.ghpages
import com.jsuereth.git.GitPlugin.git

object GpgBuild extends Build {
  val defaultSettings: Seq[Setting[_]] = Seq(
    organization := "com.jsuereth",
    version := "0.6",
    publishTo <<= (version) { v =>
      import Classpaths._
      Option(if(v endsWith "SNAPSHOT") typesafeSnapshots else typesafeResolver)
    },
    publishMavenStyle := false,
    publishTo := Some(Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))
  )

  val plugin = Project("plugin", file(".")) dependsOn(library) settings(defaultSettings:_*) settings(
    sbtPlugin := true,
    name := "xsbt-gpg-plugin"
  ) settings(websiteSettings:_*)

  lazy val library = Project("library", file("gpg-library")) settings(defaultSettings:_*) settings(
    name := "gpg-library",
    crossScalaVersions := Seq("2.9.1", "2.9.0-1", "2.9.0", "2.8.2", "2.8.1", "2.8.0"),
    libraryDependencies += "org.bouncycastle" % "bcpg-jdk16" % "1.46",
    libraryDependencies += "net.databinder" %% "dispatch-http" % "0.8.6"
  )


  def websiteSettings: Seq[Setting[_]] = site.settings ++ ghpages.settings ++ Seq(
    git.remoteRepo := "git@github.com:sbt/xsbt-gpg-plugin.git",
    siteMappings <++= (baseDirectory, target, streams) map { (dir, out, s) => 
      val jekyllSrc = dir / "src" / "jekyll"
      val jekyllOutput = out / "jekyll"
      // Run Jekyll
      sbt.Process(Seq("jekyll", jekyllOutput.getAbsolutePath), Some(jekyllSrc)).!;
      // Figure out what was generated.
      (jekyllOutput ** ("*.html" | "*.png" | "*.js" | "*.css") x relativeTo(jekyllOutput))
    },
    site.addMappingsToSiteDir(mappings in packageDoc in Compile in library, "library/latest/api")
  )
}
