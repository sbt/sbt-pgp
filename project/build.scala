import sbt._
import Keys._

//import com.jsuereth.sbtsite.SitePlugin.site
//import com.jsuereth.sbtsite.SiteKeys._
//import com.jsuereth.ghpages.GhPages.ghpages
//import com.jsuereth.git.GitPlugin.git

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
    publishTo <<= (version) { v =>
      import Classpaths._
      Option(if(v endsWith "SNAPSHOT") typesafeSnapshots else typesafeResolver)
    },
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
    name := "xsbt-gpg-plugin"
  ) settings(
    //tmp workaround
    libraryDependencies += "net.databinder.dispatch" % "core_2.9.2" % "0.9.0"
  ) //settings(websiteSettings:_*)  
  /* settings(ScriptedPlugin.scriptedSettings:_*) */

  lazy val library = Project("library", file("gpg-library")) settings(defaultSettings:_*) settings(
    name := "gpg-library",
    crossScalaVersions := Seq("2.9.1", "2.9.0-1", "2.9.0", "2.8.2", "2.8.1", "2.8.0"),
    libraryDependencies += "org.bouncycastle" % "bcpg-jdk16" % "1.46",
    libraryDependencies += "net.databinder.dispatch" % "core_2.9.2" % "0.9.0"
  ) settings(Sonatype.publishSettings(
      url="http://scala-sbt.org/xsbt-gpg-plugin/",
      gitUrl="git://github.com/sbt/xsbt-gpg-plugin.git",
      licenses=Seq(Sonatype.BSD),
      developers=Seq(Sonatype.Developer("jsuereth", "Josh Suereth"))):_*)


  /*def websiteSettings: Seq[Setting[_]] = site.settings ++ ghpages.settings ++ Seq(
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
  )*/
}
