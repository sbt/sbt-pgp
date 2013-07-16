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

import com.typesafe.sbt.SbtGit._

object GpgBuild extends Build {

  override val settings: Seq[Setting[_]] =
    super.settings ++ versionWithGit ++ Seq(
      git.baseVersion := "0.8.2"
    )

  val defaultSettings: Seq[Setting[_]] =
   Seq(
    organization := "com.jsuereth",
    publishMavenStyle := false,
    publishTo <<= (version) { version: String =>
       val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
       val (name, u) = if (version.contains("-SNAPSHOT")) ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
                       else ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
       Some(Resolver.url(name, url(u))(Resolver.ivyStylePatterns))
    }
  )

  val dispatchDependency: Setting[_] =
    libraryDependencies <+= (scalaVersion) apply { (sv) =>
      if(sv startsWith "2.9")       "net.databinder" % "dispatch-http_2.9.1" % "0.8.10"
      else                          "net.databinder" %% "dispatch-http" % "0.8.10"
    }
  val bouncyCastlePgp = "org.bouncycastle" % "bcpg-jdk15on" % "1.49"

  lazy val root = (
    Project("sbt-pgp", file(".")) 
    aggregate(plugin, library)
    settings(websiteSettings:_*)
    settings(
      publishLocal := (),
      publish := ()
    )
  )

  lazy val plugin = Project("plugin", file("pgp-plugin")) dependsOn(library) settings(defaultSettings:_*) settings(
    sbtPlugin := true,
    organization := "com.typesafe.sbt",
    name := "sbt-pgp"
  ) settings(websiteSettings:_*)  settings(
    //tmp workaround
    dispatchDependency) aggregate(library)
  /* settings(ScriptedPlugin.scriptedSettings:_*) */

  lazy val library = Project("library", file("gpg-library")) settings(defaultSettings:_*) settings(
    name := "gpg-library",
    libraryDependencies += bouncyCastlePgp,
    dispatchDependency
  ) settings(Sonatype.publishSettings(
      url="http://scala-sbt.org/sbt-pgp/",
      gitUrl="git://github.com/sbt/sbt-pgp.git",
      licenses=Seq(Sonatype.BSD),
      developers=Seq(Sonatype.Developer("jsuereth", "Josh Suereth"))):_*)


  def websiteSettings: Seq[Setting[_]] = (
    site.settings ++ 
    ghpages.settings ++ 
    site.jekyllSupport() ++ 
    site.includeScaladoc() ++ 
    Seq(
      git.remoteRepo := "git@github.com:sbt/sbt-pgp.git",
      site.addMappingsToSiteDir(mappings in packageDoc in Compile in library, "library/latest/api")
    )
  )
}
