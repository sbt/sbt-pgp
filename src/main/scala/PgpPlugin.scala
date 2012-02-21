package com.jsuereth
package pgp
package sbtplugin


import sbt._
import Keys._
import sbt.Project.Initialize
import complete.Parser
import complete.DefaultParsers._
import SbtHelpers._

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
  val pgpReadOnly = SettingKey[Boolean]("pgp-read-only", "If set to true, the PGP usage will not modify any public/private keyrings.")
  val pgpCmd = InputKey[Unit]("pgp-cmd", "Runs one of the various PGP commands.")
  val pgpStaticContext = SettingKey[cli.PgpStaticContext]("pgp-static-context", "Context used for auto-completing PGP commands.")
  val pgpCmdContext = TaskKey[cli.PgpCommandContext]("pgp-context", "Context used to run PGP commands.")
  
  // GPG Related Options
  val gpgCommand = SettingKey[String]("gpg-command", "The path of the GPG command to run")
  val useGpg = SettingKey[Boolean]("use-gpg", "If this is set to true, the GPG command line will be used.")
  val useGpgAgent = SettingKey[Boolean]("use-gpg-agent", "If this is set to true, the GPG command line will expect a GPG agent for the password.")
  
  // Checking PGP Signatures options
  val signaturesModule = TaskKey[GetSignaturesModule]("signatures-module")
  val updatePgpSignatures = TaskKey[UpdateReport]("update-pgp-signatures", "Resolves and optionally retrieves signatures for artifacts, transitively.")
  val checkPgpSignatures = TaskKey[SignatureCheckReport]("check-pgp-signatures", "Checks the signatures of artifacts to see if they are trusted.")

  /** Configuration for GPG command line */
  lazy val gpgConfigurationSettings: Seq[Setting[_]] = Seq(
    useGpg := false,
    useGpgAgent := false,
    gpgCommand := (if(isWindows) "gpg.exe" else "gpg")
  )
  /** Configuration for BC JVM-local PGP */
  lazy val nativeConfigurationSettings: Seq[Setting[_]] = Seq(
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
    pgpStaticContext <<= (pgpPublicRing, pgpSecretRing) apply SbtPgpStaticContext.apply,
    pgpCmdContext <<= (pgpStaticContext, pgpPassphrase, streams) map SbtPgpCommandContext.apply,
    pgpReadOnly := true,
    pgpCmd <<= InputTask(pgpStaticContext apply { ctx => (_: State) => Space ~> cli.PgpCommand.parser(ctx) }) { result =>
      (result, pgpCmdContext, pgpReadOnly) map { (cmd, ctx, readOnly) => 
        if(readOnly && !cmd.isReadOnly) sys.error("Cannot modify keyrings when in read-only mode.  Run `set pgpReadOnly := false` before running this command.")
        cmd run ctx 
      }
    }
  )
  
  /** Helper to initialize the BC PgpSigner */
  private[this] def bcPgpSigner: Initialize[Task[PgpSigner]] =
    (pgpCmdContext, pgpSigningKey) map (new BouncyCastlePgpSigner(_,_))
  /** Helper to initialize the GPG PgpSigner */
  private[this] def gpgSigner: Initialize[Task[PgpSigner]] =
    (gpgCommand, useGpgAgent, pgpSigningKey) map (new CommandLineGpgSigner(_, _, _))
  /** Helper to initialize the BC PgpVerifier */
  private[this] def bcPgpVerifier: Initialize[Task[PgpVerifier]] =
    pgpCmdContext map (new BouncyCastlePgpVerifier(_))
  /** Helper to initialize the GPG PgpVerifier */
  private[this] def gpgVerifier: Initialize[Task[PgpVerifier]] =
    gpgCommand map (new CommandLineGpgVerifier(_))
     
  /** These are all the configuration related settings that are common
   * for a multi-project build, and can be re-used on
   * ThisBuild or maybe Global.
   */
  lazy val configurationSettings: Seq[Setting[_]] = gpgConfigurationSettings ++ nativeConfigurationSettings ++ Seq(
    // TODO - move these to the signArtifactSettings?
    skip in pgpSigner := false,
    pgpSigner <<= switch(useGpg, gpgSigner, bcPgpSigner),
    pgpVerifier <<= switch(useGpg, gpgVerifier, bcPgpVerifier),
    pgpCmd <<= InputTask(pgpStaticContext apply { ctx => (_: State) => Space ~> cli.PgpCommand.parser(ctx) }) { result =>
      (result, pgpCmdContext, pgpReadOnly) map { (cmd, ctx, readOnly) => 
        if(readOnly && !cmd.isReadOnly) sys.error("Cannot modify keyrings when in read-only mode.  Run `set pgpReadOnly := false` before running this command.")
        cmd run ctx 
      }
    }
  ) 
  /** Configuration for signing artifacts.  If you use new scopes for
   * packagedArtifacts, you need to add this in that scope to your build.
   */
  lazy val signingSettings: Seq[Setting[_]] = Seq(
    packagedArtifacts <<= (packagedArtifacts, pgpSigner, skip in pgpSigner, streams) map {
      (artifacts, r, skipZ, s) =>
        if (!skipZ) {
          artifacts flatMap {
            case (art, file) =>
              Seq(art                                                -> file, 
                  art.copy(extension = art.extension + gpgExtension) -> r.sign(file, new File(file.getAbsolutePath + gpgExtension), s))
          }
        } else artifacts
    }
  )
  /** Settings used to verify signatures on dependent artifacts. */
  lazy val verifySettings: Seq[Setting[_]] = Seq(
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
  /** Settings this plugin defines. TODO - require manual setting of these... */
  lazy val allSettings = configurationSettings ++ signingSettings ++ verifySettings
  
  /** TODO - Deprecate this usage... */
  override val settings = allSettings
  
  def usePgpKeyHex(id: String) =
    pgpSigningKey := Some(java.lang.Long.parseLong(id, 16))
}
