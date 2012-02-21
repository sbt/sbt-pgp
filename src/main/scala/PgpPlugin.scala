package com.jsuereth
package pgp
package sbtplugin


import sbt._
import Keys._
import sbt.Project.Initialize
import complete.Parser
import complete.DefaultParsers._
import SbtHelpers._
import PgpKeys._

/**
 * Plugin for doing PGP security tasks.  Signing, verifying, etc.
 */
object PgpPlugin extends Plugin {
  // Delegates for better build.sbt configuration.
  def useGpg = PgpKeys.useGpg in Global
  def useGpgAgent = PgpKeys.useGpgAgent in Global
  def pgpSigningKey = PgpKeys.pgpSigningKey in Global
  def pgpPassphrase = PgpKeys.pgpPassphrase in Global
  def pgpReadOnly = PgpKeys.pgpReadOnly in Global
  def pgpPublicRing = PgpKeys.pgpPublicRing in Global
  def pgpSecretRing = PgpKeys.pgpSecretRing in Global
  
  /** Configuration for GPG command line */
  lazy val gpgConfigurationSettings: Seq[Setting[_]] = inScope(GlobalScope)(Seq(
    initIf(PgpKeys.useGpg, false),
    initIf(PgpKeys.useGpgAgent, false),
    initIf(PgpKeys.gpgCommand, (if(isWindows) "gpg.exe" else "gpg"))
  ))
  /** Configuration for BC JVM-local PGP */
  lazy val nativeConfigurationSettings: Seq[Setting[_]] = inScope(GlobalScope)(Seq(
    initIf(PgpKeys.pgpPassphrase, None),
    initIf(PgpKeys.pgpPublicRing, file(System.getProperty("user.home")) / ".gnupg" / "pubring.gpg"),
    initIf(PgpKeys.pgpSecretRing, file(System.getProperty("user.home")) / ".gnupg" / "secring.gpg"),
    initIf(PgpKeys.pgpSigningKey, None),
    initIf(PgpKeys.pgpReadOnly, true),
    // TODO - Are these all ok to place in global scope?
    PgpKeys.pgpPublicRing <<= PgpKeys.pgpPublicRing apply {
      case f if f.exists => f
      case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "pubring.asc"
    },
    PgpKeys.pgpSecretRing <<= PgpKeys.pgpSecretRing apply {
      case f if f.exists => f
      case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "secring.asc"
    },
    PgpKeys.pgpStaticContext <<= (PgpKeys.pgpPublicRing, PgpKeys.pgpSecretRing) apply SbtPgpStaticContext.apply,
    PgpKeys.pgpCmdContext <<= (PgpKeys.pgpStaticContext, PgpKeys.pgpPassphrase, streams) map SbtPgpCommandContext.apply,
    pgpCmd <<= InputTask(pgpStaticContext apply { ctx => (_: State) => Space ~> cli.PgpCommand.parser(ctx) }) { result =>
      (result, pgpCmdContext, pgpReadOnly) map { (cmd, ctx, readOnly) => 
        if(readOnly && !cmd.isReadOnly) sys.error("Cannot modify keyrings when in read-only mode.  Run `set pgpReadOnly := false` before running this command.")
        cmd run ctx 
      }
    }
  ))
  
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
    pgpVerifier <<= switch(useGpg, gpgVerifier, bcPgpVerifier)
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
