import sbt._
import sbt.Keys._

object Bintray {
  val bintrayPublishAllStaged = taskKey[Unit]("Publish all staged artifacts on bintray.")
  val checkBintrayCredentials = taskKey[Unit]("Checks to see if bintray credentials are configured.")
  val bintrayRepoId = settingKey[String]("The project id in bintray.")
  val bintrayUserId = settingKey[String]("The user we have in bintray.")
  val bintrayPublishToUrl = settingKey[String]("The url we'll publish to.")
  val bintrayLayout = settingKey[String]("pattern we use when publishing to bintray.")
  // val bintrayPluginUrl = "https://api.bintray.com/content/sbt/sbt-plugin-releases/"
  //val bintrayPluginLayout = "[module]/[revision]/"+ Resolver.localBasePattern

  def bintrayCreds(creds: Seq[sbt.Credentials]): (String, String) = {
    val matching = 
      for {
        c <- creds
        if c.isInstanceOf[sbt.DirectCredentials]
        val cred = c.asInstanceOf[sbt.DirectCredentials]
        if cred.host == "api.bintray.com"
      } yield cred.userName -> cred.passwd

    matching.headOption getOrElse sys.error("Unable to find bintray credentials (api.bintray.com)")
  }

  def publishContent(pkg: String, subject: String, repo: String, version: String, creds: Seq[sbt.Credentials]): Unit = {
    val uri = s"https://bintray.com/api/v1/content/$subject/$repo/$pkg/$version/publish"
    val (u,p) = bintrayCreds(creds)
    import dispatch.classic._
    // TODO - Log the output
    Http(url(uri).POST.as(u,p).>|)
  }

  def settings: Seq[Setting[_]] = 
    Seq(
       bintrayRepoId := "sbt-plugin-releases",
       bintrayUserId := "sbt",
       bintrayLayout := s"${normalizedName.value}/[revision]/${Resolver.localBasePattern}",
       bintrayPublishToUrl := s"https://api.bintray.com/content/${bintrayUserId.value}/${bintrayRepoId.value}/",
       publishTo := {
         val resolver = Resolver.url("bintray-"+bintrayRepoId.value, new URL(bintrayPublishToUrl.value))(Patterns(false, bintrayLayout.value))
         Some(resolver)
       },
       checkBintrayCredentials := {
       	 val creds = credentials.value
       	 val (user, _) = bintrayCreds(creds)
       	 streams.value.log.info(s"Using $user for bintray login.") 
       },
       bintrayPublishAllStaged := {
       	 val creds = credentials.value
       	 publishContent(projectID.value.name, bintrayUserId.value, bintrayRepoId.value, version.value, creds)
       }
    )
}