package com.jsuereth
package pgp

import sbt._
import Keys._
import sbt.Project.Initialize
import complete.Parser
import complete.DefaultParsers._


object GpgPlugin extends Plugin {
  val gpgCommand = SettingKey[String]("gpg-command", "The path of the GPG command to run")
  val gpgRunner = TaskKey[PgpSigner]("gpg-runner", "The helper class to run GPG commands.")  
  val gpgVerifier = TaskKey[PgpVerifier]("gpg-verifier", "The helper class to verify public keys from a public key ring.")  
  val gpgSecretRing = SettingKey[File]("gpg-secret-ring", "The location of the secret key ring.  Only needed if using bouncy castle.")
  val gpgPublicRing = SettingKey[File]("gpg-public-ring", "The location of the secret key ring.  Only needed if using bouncy castle.")
  val gpgPassphrase = SettingKey[Option[Array[Char]]]("gpg-passphrase", "The passphrase associated with the secret used to sign artifacts.")
  val gpgGenKey = InputKey[Unit]("gpg-gen-key", "Creates a new PGP key using bouncy castle.   Must provide <name> <email>.  The passphrase setting must be set for this to work.")
  val useGpg = SettingKey[Boolean]("use-gpg", "If this is set to true, the GPG command line will be used.")
  val useGpgAgent = SettingKey[Boolean]("use-gpg-agent", "If this is set to true, the GPG command line will expect a GPG agent for the password.")
  val signaturesModule = TaskKey[GetSignaturesModule]("signatures-module")
  val updatePgpSignatures = TaskKey[UpdateReport]("update-pgp-signatures", "Resolves and optionally retrieves signatures for artifacts, transitively.")
  val checkPgpSignatures = TaskKey[SignatureCheckReport]("check-pgp-signatures", "Checks the signatures of artifacts to see if they are trusted.")

  // TODO - home dir, use-agent, 
  // TODO - --batch and pasphrase and read encrypted passphrase...
  // TODO --local-user
  // TODO --detach-sign
  // TODO --armor
  // TODO --no-tty
  // TODO  Signature extension
  
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
    gpgRunner <<= (gpgSecretRing, gpgPassphrase, gpgCommand, useGpg, useGpgAgent) map { (secring, optPass, command, b, agent) =>
      if(b) new CommandLineGpgSigner(command, agent)
      else {
        val p = optPass getOrElse readPassphrase()
        new BouncyCastlePgpSigner(secring, p)
      }
    },
    gpgVerifier <<= (gpgPublicRing, gpgCommand, useGpg) map { (pubring, command, b) =>
      if(b) new CommandLineGpgVerifier(command)
      else new BouncyCastlePgpVerifier(pubring)
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
    // TODO - This is checking SBT and its plugins signatures..., maybe we can have this be a separate config or something.
    /*signaturesModule in updateClassifiers <<= (projectID, sbtDependency, loadedBuild, thisProjectRef) map { ( pid, sbtDep, lb, ref) =>
			val pluginIDs: Seq[ModuleID] = lb.units(ref.build).unit.plugins.fullClasspath.flatMap(_ get moduleID.key)
			GetSignaturesModule(pid, sbtDep +: pluginIDs, Configurations.Default :: Nil)
		},*/
    signaturesModule in updatePgpSignatures <<= (projectID, libraryDependencies) map { ( pid, deps) =>
      GetSignaturesModule(pid, deps, Configurations.Default :: Nil)
    },
    updatePgpSignatures <<= (ivySbt, 
                          signaturesModule in updatePgpSignatures, 
                          updateConfiguration, 
                          ivyScala, 
                          target in LocalRootProject, 
                          appConfiguration, 
                          streams) map { (is, mod, c, ivyScala, out, app, s) =>
      PgpSignatureCheck.resolveSignatures(is, GetSignaturesConfiguration(mod, c, ivyScala), s.log)
    },
    checkPgpSignatures <<= (updatePgpSignatures, gpgVerifier, streams) map PgpSignatureCheck.checkSignaturesTask
  )

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


  // Helper to figure out how to run GPG signing...
  def isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1
}
