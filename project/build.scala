import sbt._
import Keys._

import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit.git

/** Helper object for creating Sonatype OSSRH metadata. */ 
// TODO - Make this a plugin
object Sonatype {
  case class Developer(id: String, name: String) {
    def toXml = 
      (<developer>
          <id>{id}</id>
          <name>{name}</name>
        </developer>)
  }
  
  case class License(name: String, url: String, distribution: String = "repo") {
    def toXml = 
      (<license>
         <name>{name}</name>
         <url>{url}</url>
         <distribution>{distribution}</distribution>
       </license>)
  }
  
  val BSD = License("BSD", "ttp://www.opensource.org/licenses/bsd-license.php")
  
  def publishSettings(gitUrl:String, url: String, licenses: Seq[License], developers: Seq[Developer]): Seq[Setting[_]] = Seq(
    // If we want on maven central, we need to be in maven style.
    publishMavenStyle := true,
    publishArtifact in Test := false,
    // The Nexus repo we're publishing to.
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots") 
      else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    // Maven central cannot allow other repos.  We're ok here because the artifacts we
    // we use externally are *optional* dependencies.
    pomIncludeRepository := { x => false },
    // Maven central wants some extra metadata to keep things 'clean'.
    pomExtra := (
      <url>{url}</url>
      <licenses>
        { licenses map (_.toXml) }
      </licenses>
      <scm>
        <url>{gitUrl}</url>
        <connection>scm:{gitUrl}</connection>
      </scm>
      <developers>
        { developers map (_.toXml) }
      </developers>)
  )
  
  
}

object GpgBuild extends Build {
  val defaultSettings: Seq[Setting[_]] = Seq(
    organization := "com.jsuereth",
    version := "0.7-SNAPSHOT",
    publishMavenStyle := false,
    publishTo <<= (version) { version: String =>
       val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
       val (name, u) = if (version.contains("-SNAPSHOT")) ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
                       else ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
       Some(Resolver.url(name, url(u))(Resolver.ivyStylePatterns))
    }
  )

  val plugin = Project("plugin", file(".")) dependsOn(library) settings(defaultSettings:_*) settings(
    sbtPlugin := true,
    organization := "com.typesafe.sbt",
    name := "sbt-pgp"
  ) settings(websiteSettings:_*)  settings(
    //tmp workaround
    libraryDependencies += "net.databinder" % "dispatch-http_2.9.1" % "0.8.6") aggregate(library)
  /* settings(ScriptedPlugin.scriptedSettings:_*) */

  lazy val library = Project("library", file("gpg-library")) settings(defaultSettings:_*) settings(
    name := "gpg-library",
    crossScalaVersions := Seq("2.9.1", "2.9.0-1", "2.9.0", "2.8.2", "2.8.1", "2.8.0"),
    libraryDependencies += "org.bouncycastle" % "bcpg-jdk16" % "1.46",
    libraryDependencies += "net.databinder" % "dispatch-http_2.9.1" % "0.8.6"
  ) settings(Sonatype.publishSettings(
      url="http://scala-sbt.org/sbt-gpg/",
      gitUrl="git://github.com/sbt/sbt-gpg.git",
      licenses=Seq(Sonatype.BSD),
      developers=Seq(Sonatype.Developer("jsuereth", "Josh Suereth"))):_*)


  def websiteSettings: Seq[Setting[_]] = (
    site.settings ++ 
    ghpages.settings ++ 
    site.jekyllSupport() ++ 
    site.includeScaladoc() ++ 
    Seq(
      git.remoteRepo := "git@github.com:sbt/xsbt-gpg-plugin.git",
      site.addMappingsToSiteDir(mappings in packageDoc in Compile in library, "library/latest/api")
    )
  )
}
