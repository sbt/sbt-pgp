package com.typesafe.sbt
package pgp


import sbt._
import Keys._
import complete.DefaultParsers._
import SbtHelpers._
import PgpKeys._
import com.jsuereth.pgp._
import scala.concurrent.duration._
import sbt.sbtpgp.Compat, Compat._

/**
 * SBT Settings for doing PGP security tasks.  Signing, verifying, etc.
 */
object PgpSettings {
  // Delegates for better build.sbt configuration.
  // TODO - DO these belong lower?
  def useGpg = PgpKeys.useGpg in Global
  def useGpgAgent = PgpKeys.useGpgAgent in Global
  def pgpSigningKey = PgpKeys.pgpSigningKey in Global
  def pgpPassphrase = PgpKeys.pgpPassphrase in Global
  def pgpReadOnly = PgpKeys.pgpReadOnly in Global
  def pgpPublicRing = PgpKeys.pgpPublicRing in Global
  def pgpSecretRing = PgpKeys.pgpSecretRing in Global

  /** Configuration for GPG command line */
  lazy val gpgConfigurationSettings: Seq[Setting[_]] = Seq( 
    PgpKeys.useGpg := false,
    PgpKeys.useGpgAgent := false,
    PgpKeys.gpgCommand := (if(isWindows) "gpg.exe" else "gpg")
  )

  lazy val pgpCommand = Command("pgp-cmd") {
    state =>
      val extracted = Project.extract(state)
      val ctx = extracted.get(pgpStaticContext)
      Space ~> cli.PgpCommand.parser(ctx)
  } { (state, cmd) =>
    val extracted = Project.extract(state)
    val readOnly = extracted get pgpReadOnly
    if(readOnly && !cmd.isReadOnly) sys.error("Cannot modify keyrings when in read-only mode.  Run `set pgpReadOnly := false` before running this command.")
    def runPgpCmd(ctx: cli.PgpCommandContext): Unit =
      try cmd run ctx
      catch {
        case e: Exception =>
          System.err.println("Failed to run pgp-cmd: " + cmd + ".   Please report this issue at http://github.com/sbt/sbt-pgp/issues")
          throw e
      }
    // Create a new task that executes the command.
    val task = extracted get pgpCmdContext map runPgpCmd named ("pgp-cmd-" + cmd.getClass.getSimpleName)
    import EvaluateTask._
    val (newstate, _) = withStreams(extracted.structure, state) { streams =>
      val config = EvaluateTaskConfig(
        restrictions = defaultRestrictions(1),
        checkCycles = false,
        progressReporter = Compat.defaultProgress,
        cancelStrategy = TaskCancellationStrategy.Null,
        forceGarbageCollection = false,
        minForcegcInterval = 60.seconds)
      EvaluateTask.runTask(task, state, streams, extracted.structure.index.triggers, config)(nodeView(state, streams, Nil))
    }
    newstate
  }

  /** Configuration for BC JVM-local PGP */
  lazy val nativeConfigurationSettings: Seq[Setting[_]] = {
    val gnuPGHome = scala.util.Properties.envOrNone("GNUPGHOME") match {
      case Some(dir) => file(dir)
      case None => file(System.getProperty("user.home")) / ".gnupg"
    }
    Seq(
      PgpKeys.pgpPassphrase := None,
      PgpKeys.pgpSelectPassphrase := PgpKeys.pgpPassphrase.value orElse
        (Credentials.forHost(credentials.value, "pgp") map (_.passwd.toCharArray)),
      PgpKeys.pgpPublicRing := gnuPGHome / "pubring.gpg",
      PgpKeys.pgpSecretRing := gnuPGHome / "secring.gpg",
      PgpKeys.pgpSigningKey := None,
      PgpKeys.pgpReadOnly := true,
      // TODO - Are these all ok to place in global scope?
      PgpKeys.pgpPublicRing := {
        val old = PgpKeys.pgpPublicRing.value
        old match {
          case f if f.exists => f
          case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "pubring.asc"
        }
      },
      PgpKeys.pgpSecretRing := {
        val old = PgpKeys.pgpSecretRing.value
        old match {
          case f if f.exists => f
          case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "secring.asc"
        }
      },
      PgpKeys.pgpStaticContext := {
        SbtPgpStaticContext(PgpKeys.pgpPublicRing.value, PgpKeys.pgpSecretRing.value)
      },
      PgpKeys.pgpCmdContext := {
        SbtPgpCommandContext(
          PgpKeys.pgpStaticContext.value,
          interactionService.value,
          PgpKeys.pgpSelectPassphrase.value,
          streams.value)
      }
    )
  }

  /** Helper to initialize the BC PgpSigner */
  private[this] def bcPgpSigner: Def.Initialize[Task[PgpSigner]] =
    (pgpCmdContext, pgpSigningKey) map (new BouncyCastlePgpSigner(_,_))
  /** Helper to initialize the GPG PgpSigner */
  private[this] def gpgSigner: Def.Initialize[Task[PgpSigner]] =
    (gpgCommand, useGpgAgent, pgpSigningKey) map (new CommandLineGpgSigner(_, _, _))
  /** Helper to initialize the BC PgpVerifier */
  private[this] def bcPgpVerifierFactory: Def.Initialize[Task[PgpVerifierFactory]] =
    pgpCmdContext  map (new BouncyCastlePgpVerifierFactory(_))
  /** Helper to initialize the GPG PgpVerifier */
  private[this] def gpgVerifierFactory: Def.Initialize[Task[PgpVerifierFactory]] =
    (gpgCommand, pgpCmdContext) map (new CommandLineGpgVerifierFactory(_, _))

  /** These are all the configuration related settings that are common
   * for a multi-project build, and can be re-used on
   * ThisBuild or maybe Global.
   */
  lazy val signVerifyConfigurationSettings: Seq[Setting[_]] = Seq(
    // TODO - move these to the signArtifactSettings?
    skip in pgpSigner := ((skip in pgpSigner) ?? false).value,
    pgpSigner := switch(useGpg, gpgSigner, bcPgpSigner).value,
    pgpVerifierFactory := switch(useGpg, gpgVerifierFactory, bcPgpVerifierFactory).value
  )

  /** Configuration for signing artifacts.  If you use new scopes for
   * packagedArtifacts, you need to add this in that scope to your build.
   *
   * Right now, this also adds duplicate "publish" tasks that will ensure signed
   * artifacts.   While this isn't as friendly to other plugins that want to
   * use our signed artifacts in normal publish flow, it should be more user friendly.
   */
  lazy val signingSettings: Seq[Setting[_]] = Seq(
    signedArtifacts := {
      val artifacts = packagedArtifacts.value
      val r = pgpSigner.value
      val skipZ = (skip in pgpSigner).value
      val s = streams.value
      if (!skipZ) {
        artifacts flatMap {
          case (art, file) =>
            Seq(art                                                -> file,
                subExtension(art, art.extension + gpgExtension) -> r.sign(file, new File(file.getAbsolutePath + gpgExtension), s))
        }
      }
      else artifacts
    },
    publishSignedConfiguration := {
      Classpaths.publishConfig(
        signedArtifacts.value,
        if (publishMavenStyle.value) None
        else Some(deliver.value),
        resolverName = Classpaths.getPublishTo(publishTo.value).name,
        checksums = (checksums in publish).value,
        logging = ivyLoggingLevel.value)
    },
    publishSigned := publishSignedTask(publishSignedConfiguration, deliver).value,
    publishLocalSignedConfiguration := {
      Classpaths.publishConfig(
        signedArtifacts.value,
        Some(deliverLocal.value),
        (checksums in publishLocal).value,
        logging = ivyLoggingLevel.value)
    },
    publishLocalSigned := publishSignedTask(publishLocalSignedConfiguration, deliver).value
  )

  def publishSignedTask(config: TaskKey[PublishConfiguration], deliverKey: TaskKey[_]): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      val s = streams.value
      val ref = thisProjectRef.value
      val skp = ((skip in publish) ?? false).value
      if (skp) Def.task { s.log.debug(s"Skipping publishSigned for ${ref.project}") }
      else Classpaths.publishTask(config, deliver)
    }

  /** Settings used to verify signatures on dependent artifacts. */
  lazy val verifySettings: Seq[Setting[_]] = Seq(
    // TODO - This is checking SBT and its plugins signatures..., maybe we can have this be a separate config or something.
    /*signaturesModule in updateClassifiers <<= (projectID, sbtDependency, loadedBuild, thisProjectRef) map { ( pid, sbtDep, lb, ref) =>
      val pluginIDs: Seq[ModuleID] = lb.units(ref.build).unit.plugins.fullClasspath.flatMap(_ get moduleID.key)
      GetSignaturesModule(pid, sbtDep +: pluginIDs, Configurations.Default :: Nil)
    },*/
    signaturesModule in updatePgpSignatures := {
      GetSignaturesModule(projectID.value, libraryDependencies.value, Configurations.Default :: Nil)
    },
    updatePgpSignatures := {
      PgpSignatureCheck.resolveSignatures(
        ivySbt.value,
        GetSignaturesConfiguration((signaturesModule in updatePgpSignatures).value, updateConfiguration.value, ivyScala.value),
        streams.value.log)
    },
    checkPgpSignatures := {
      PgpSignatureCheck.checkSignaturesTask(updatePgpSignatures.value, pgpVerifierFactory.value, streams.value)
    }
  )
  lazy val globalSettings: Seq[Setting[_]] = inScope(Global)(gpgConfigurationSettings ++ nativeConfigurationSettings ++ signVerifyConfigurationSettings)
  /** Settings this plugin defines. TODO - require manual setting of these... */
  lazy val projectSettings = signingSettings ++ verifySettings ++ Seq(commands += pgpCommand)
}
