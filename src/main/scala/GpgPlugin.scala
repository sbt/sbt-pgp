package com.jsuereth.pgp

import sbt._
import Keys._
import sbt.Project.Initialize
import complete.Parser
import complete.DefaultParsers._


object GpgPlugin extends Plugin {
  val gpgCommand = SettingKey[String]("gpg-command", "The path of the GPG command to run")
  val gpgRunner = TaskKey[PgpSigner]("gpg-runner", "The helper class to run GPG commands.")  
  val gpgSecretRing = SettingKey[File]("gpg-secret-ring", "The location of the secret key ring.  Only needed if using bouncy castle.")
  val gpgPublicRing = SettingKey[File]("gpg-public-ring", "The location of the secret key ring.  Only needed if using bouncy castle.")
  val gpgPassphrase = SettingKey[Option[Array[Char]]]("gpg-passphrase", "The passphrase associated with the secret used to sign artifacts.")
  val gpgGenKey = InputKey[Unit]("gpg-gen-key", "Creates a new PGP key using bouncy castle.   Must provide <name> <email>.  The passphrase setting must be set for this to work.")
  val useGpg = SettingKey[Boolean]("use-gpg", "If this is set to true, the GPG command line will be used.")
  val useGpgAgent = SettingKey[Boolean]("use-gpg-agent", "If this is set to true, the GPG command line will expect a GPG agent for the password.")
  val signaturesModule = TaskKey[GetSignaturesModule]("signatures-module")
	val updateSignatures = TaskKey[UpdateReport]("update-signatures", "Resolves and optionally retrieves signatures for artifacts, transitively.")
	val checkSignatures = TaskKey[Unit]("check-signatures", "Checks the signatures of artifacts to see if they are trusted.")

  // TODO - home dir, use-agent, 
  // TODO - --batch and pasphrase and read encrypted passphrase...
  // TODO --local-user
  // TODO --detach-sign
  // TODO --armor
  // TODO --no-tty
  // TODO  Signature extension
  private[this] val gpgExtension = ".asc"
  
  override val settings = Seq(
    skip in gpgRunner := false,
    useGpg := false,
    useGpgAgent := false,
    gpgCommand := (if(isWindows) "gpg.exe" else "gpg"),
    gpgPassphrase := None,
    gpgPublicRing := file(System.getProperty("user.home")) / ".gnupg" / "pubring.gpg",
    gpgSecretRing := file(System.getProperty("user.home")) / ".gnupg" / "secring.gpg",
    // If the user isn't using GPG, we'll use a bouncy-castle ring.
    gpgPublicRing <<= gpgPublicRing apply {
      case f if f.exists => f
      case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "pubring.asc"
    },
    gpgSecretRing <<= gpgSecretRing apply {
      case f if f.exists => f
      case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "secring.asc"
    },
    // TODO - Select the runner based on the existence of the gpg/gpg.exe command rather than the configuring of a passPhrase.
    gpgRunner <<= (gpgSecretRing, gpgPassphrase, gpgCommand, useGpg, useGpgAgent) map { (secring, optPass, command, b, agent) =>
      // TODO - Catch errors and report issues.
      if(b) new CommandLineGpgSigner(command, agent)
      else {
        val p = optPass getOrElse readPassphrase()
        new BouncyCastlePgpSigner(secring, p)
      }
    },
    packagedArtifacts <<= (packagedArtifacts, gpgRunner, skip in gpgRunner, streams) map {
      (artifacts, r, skipZ, s) =>
        if (!skipZ) {
          artifacts flatMap {
            case (art, file) =>
              Seq(art                                                -> file, 
                  art.copy(extension = art.extension + gpgExtension) -> r.sign(file, new File(file.getAbsolutePath + gpgExtension), s))
          }
        } else artifacts
    },
    gpgGenKey <<= InputTask(keyGenParser)(keyGenTask),
    signaturesModule in updateClassifiers <<= (projectID, sbtDependency, loadedBuild, thisProjectRef) map { ( pid, sbtDep, lb, ref) =>
			val pluginIDs: Seq[ModuleID] = lb.units(ref.build).unit.plugins.fullClasspath.flatMap(_ get moduleID.key)
			GetSignaturesModule(pid, sbtDep +: pluginIDs, Configurations.Default :: Nil)
		},
    updateSignatures <<= (ivySbt, 
                           signaturesModule in updateClassifiers, 
                           updateConfiguration, 
                           ivyScala, 
                           target in LocalRootProject, 
                           appConfiguration, 
                           streams) map { (is, mod, c, ivyScala, out, app, s) =>
				updateSignaturesTask(is, GetSignaturesConfiguration(mod, c, ivyScala), s.log)
		},
    checkSignatures <<= (updateSignatures, streams) map checkSignaturesTask
  )


  private[this] def checkSignaturesTask(update: UpdateReport, s: TaskStreams): Unit = {
    checkArtifactsWithSignatures(update,s)
    warnMissingSignatures(update,s)
  }
  private def warnMissingSignatures(update: UpdateReport, s: TaskStreams): Unit = 
    for {
      config <- update.configurations
      module <- config.modules
      artifact <- module.missingArtifacts
      if artifact.extension endsWith gpgExtension
    } s.log.warn("Missing signature for  %s" format (module.module))

  private def checkArtifactsWithSignatures(update: UpdateReport, s: TaskStreams): Unit =
    for {
      config <- update.configurations
      module <- config.modules
      (artifact, file) <- module.artifacts
      if file.getName endsWith gpgExtension
    } println("Checking signature file: " + file.getAbsolutePath)

  /* Reads the passphrase from the console. */
  private[this] def readPassphrase(): Array[Char] = System.out.synchronized {
    (SimpleReader.readLine("Please enter your PGP passphrase> ", Some('*')) getOrElse error("No password provided.")).toCharArray
  }
  

  private[this] def keyGenParser: State => Parser[(String,String)] = {
      (state: State) =>
        val Email: Parser[String] =  (NotSpace ~ '@' ~ NotSpace ~ '.' ~ NotSpace) map { 
          case name ~ at ~ address ~ dot ~ subdomain => 
            new StringBuilder(name).append(at).append(address).append(dot).append(subdomain).toString
        }
        val name: Parser[String] = (any*) map (_ mkString "")
        (Space ~> token(Email) ~ (Space ~> token(name)))
  }
  private[this] def keyGenTask = { (parsed: TaskKey[(String,String)]) => 
    (parsed, gpgPublicRing, gpgSecretRing, gpgRunner, streams) map { (input, pub, sec, runner, s) =>
      if(pub.exists)  error("Public key ring (" + pub.getAbsolutePath + ") already exists!")
      if(sec.exists)  error("Secret key ring (" + sec.getAbsolutePath + ") already exists!")
      val (email, name) = input
      val identity = "%s <%s>".format(name, email)
      runner.generateKey(pub, sec, identity, s)
    }
  }

  private[this] def updateSignaturesTask(ivySbt: IvySbt, config: GetSignaturesConfiguration, log: Logger): UpdateReport = {
    def restrictedCopy(m: ModuleID, confs: Boolean) =
      ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion, extraAttributes = m.extraAttributes, configurations = if(confs) m.configurations else None)
    def signatureArtifacts(m: ModuleID): Option[ModuleID] = {
      // TODO - Some kind of filtering
      // TODO - We *can't* assume everything is a jar
      Some(m.copy(explicitArtifacts = Seq(Artifact(m.name, "jar", "jar"+gpgExtension))))
    }
    import config.{configuration => c, module => mod, _}
    import mod.{configurations => confs, _}
    val baseModules = modules map { m => restrictedCopy(m, true) }
    val deps = baseModules.distinct flatMap signatureArtifacts
    val base = restrictedCopy(id, true)
    val module = new ivySbt.Module(InlineConfiguration(base, ModuleInfo(base.name), deps).copy(ivyScala = ivyScala, configurations = confs))
    val upConf = new UpdateConfiguration(c.retrieve, true, c.logging)
		IvyActions.update(module, upConf, log)
	}



  // Helper to figure out how to run GPG signing...
  def isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1
}

final case class GetSignaturesModule(id: ModuleID, modules: Seq[ModuleID], configurations: Seq[Configuration])
final case class GetSignaturesConfiguration(module: GetSignaturesModule, 
                                            configuration: UpdateConfiguration, 
                                            ivyScala: Option[IvyScala])
