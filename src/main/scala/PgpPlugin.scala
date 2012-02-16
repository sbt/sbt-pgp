package com.jsuereth
package pgp
package sbtplugin


import sbt._
import Keys._
import sbt.Project.Initialize
import complete.Parser
import complete.DefaultParsers._

/**
 * Plugin for doing PGP security tasks.  Signing, verifying, etc.
 */
object PgpPlugin extends Plugin {
  
  // PGP related setup
  val pgpSigner     = TaskKey[PgpSigner]("pgp-signer", "The helper class to run GPG commands.")  
  val pgpVerifier   = TaskKey[PgpVerifier]("pgp-verifier", "The helper class to verify public keys from a public key ring.")
  val pgpSecretRing = SettingKey[File]("pgp-secret-ring", "The location of the secret key ring.  Only needed if using bouncy castle.")
  val pgpPublicRing = SettingKey[File]("pgp-public-ring", "The location of the secret key ring.  Only needed if using bouncy castle.")
  val pgpPassphrase = SettingKey[Option[Array[Char]]]("pgp-passphrase", "The passphrase associated with the secret used to sign artifacts.")
  val pgpSigningKey = SettingKey[Option[Long]]("pgp-signing-key", "The key used to sign artifacts in this project.  Must be the full key id (not just lower 32 bits).")
  
  // PGP Related tasks  (TODO - make these commands?)
  val pgpCmd = InputKey[Unit]("pgp-cmd", "Runs one of the various PGP commands.")
  val pgpCmdContext = TaskKey[cli.PgpCommandContext]("pgp-context", "Context used to run PGP commands.")
  
  // GPG Related Options
  val gpgCommand = SettingKey[String]("gpg-command", "The path of the GPG command to run")
  val useGpg = SettingKey[Boolean]("use-gpg", "If this is set to true, the GPG command line will be used.")
  val useGpgAgent = SettingKey[Boolean]("use-gpg-agent", "If this is set to true, the GPG command line will expect a GPG agent for the password.")
  
  // Checking PGP Signatures options
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
    skip in pgpSigner := false,
    useGpg := false,
    useGpgAgent := false,
    gpgCommand := (if(isWindows) "gpg.exe" else "gpg"),
    pgpPassphrase := None,
    pgpPublicRing := file(System.getProperty("user.home")) / ".gnupg" / "pubring.gpg",
    pgpSecretRing := file(System.getProperty("user.home")) / ".gnupg" / "secring.gpg",
    pgpSigningKey := None,
    // If the user isn't using GPG, we'll use a bouncy-castle ring.
    pgpPublicRing <<= pgpPublicRing apply {
      case f if f.exists => f
      case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "pubring.asc"
    },
    pgpSecretRing <<= pgpSecretRing apply {
      case f if f.exists => f
      case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "secring.asc"
    },
    pgpCmdContext <<= (pgpSecretRing, pgpPublicRing, pgpPassphrase, streams) map SbtPgpCommandContext.apply,
    pgpCmd <<= InputTask(_ => Space ~> cli.PgpCommand.parser) { result =>
      (result, pgpCmdContext) map { (cmd, ctx) => cmd run ctx }
    },
    pgpSigner <<= (pgpSecretRing, pgpSigningKey, pgpPassphrase, gpgCommand, useGpg, useGpgAgent) map { (secring, optKey, optPass, command, b, agent) =>
      if(b) new CommandLineGpgSigner(command, agent, optKey)
      else {
        val p = optPass getOrElse readPassphrase()
        new BouncyCastlePgpSigner(secring, p, optKey)
      }
    },
    pgpVerifier <<= (pgpPublicRing, gpgCommand, useGpg) map { (pubring, command, b) =>
      if(b) new CommandLineGpgVerifier(command)
      else new BouncyCastlePgpVerifier(pubring)
    },
    packagedArtifacts <<= (packagedArtifacts, pgpSigner, skip in pgpSigner, streams) map {
      (artifacts, r, skipZ, s) =>
        if (!skipZ) {
          artifacts flatMap {
            case (art, file) =>
              Seq(art                                                -> file, 
                  art.copy(extension = art.extension + gpgExtension) -> r.sign(file, new File(file.getAbsolutePath + gpgExtension), s))
          }
        } else artifacts
    },
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
    checkPgpSignatures <<= (updatePgpSignatures, pgpVerifier, streams) map PgpSignatureCheck.checkSignaturesTask
  )
  
  def usePgpKeyHex(id: String) =
    pgpSigningKey := Some(java.lang.Long.parseLong(id, 16))
}
