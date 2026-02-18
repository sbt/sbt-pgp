package com.jsuereth.sbtpgp

import sbt._
import Keys._
import SbtHelpers._
import PgpKeys._
import sbt.sbtpgp.Compat, Compat._
import sbtcompat.PluginCompat._

/**
 * SBT Settings for doing PGP security tasks.  Signing, verifying, etc.
 */
object PgpSettings {
  lazy val globalSettings: Seq[Setting[?]] =
    inScope(Global)(gpgConfigurationSettings ++ bouncyCastleConfigurationSettings ++ signVerifyConfigurationSettings)

  /** Settings this plugin defines. TODO - require manual setting of these... */
  lazy val projectSettings: Seq[Setting[?]] = signingSettings ++ verifySettings

  def deliverPattern(outputPath: File): String =
    (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath

  /** Configuration for GPG command line */
  lazy val gpgConfigurationSettings: Seq[Setting[?]] = Seq(
    useGpg := {
      sys.props.get("SBT_PGP_USE_GPG") match {
        case Some(_) => java.lang.Boolean.getBoolean("SBT_PGP_USE_GPG")
        case None    => true
      }
    },
    useGpgAgent := true,
    useGpgPinentry := false,
    gpgCommand := (if (isWindows) "gpg.exe" else "gpg")
  )

  /** Configuration for BC JVM-local PGP */
  lazy val bouncyCastleConfigurationSettings: Seq[Setting[?]] = {
    val gnuPGHome = scala.util.Properties.envOrNone("GNUPGHOME") match {
      case Some(dir) => file(dir)
      case None      => file(System.getProperty("user.home")) / ".gnupg"
    }

    def fallbackFiles(fs: File*): File = {
      require(!fs.isEmpty)
      val (h, t) = (fs.head, fs.tail)
      if (t.isEmpty) h
      else if (h.exists) h
      else fallbackFiles(t: _*)
    }

    Seq(
      pgpPassphrase := None,
      pgpSelectPassphrase := pgpPassphrase.value
        .orElse(credentialForHost(credentials.value, "pgp").map(_.passwd.toCharArray))
        .orElse(scala.util.Properties.envOrNone("PGP_PASSPHRASE").map(_.toCharArray)),
      pgpSigningKey := credentialForHost(credentials.value, "pgp").map(_.userName),
      pgpKeyRing := None,
      // Bouncy Castle only
      pgpPublicRing := {
        fallbackFiles(
          gnuPGHome / "pubring.gpg",
          file(System.getProperty("user.home")) / ".sbt" / "gpg" / "pubring.asc"
        )
      },
      // Bouncy Castle only
      pgpSecretRing := {
        fallbackFiles(
          gnuPGHome / "secring.gpg",
          file(System.getProperty("user.home")) / ".sbt" / "gpg" / "secring.asc"
        )
      },
      pgpStaticContext := {
        SbtPgpStaticContext(pgpPublicRing.value, pgpSecretRing.value)
      },
      pgpCmdContext := {
        SbtPgpCommandContext(
          pgpStaticContext.value,
          interactionService.value,
          pgpSelectPassphrase.value,
          streams.value
        )
      }
    )
  }

  /** Helper to initialize the BC PgpSigner */
  private[this] def bcPgpSigner: Def.Initialize[Task[PgpSigner]] = Def.task {
    new BouncyCastlePgpSigner(pgpCmdContext.value, pgpSigningKey.value)
  }

  /** Helper to initialize the GPG PgpSigner */
  private[this] def gpgSigner: Def.Initialize[Task[PgpSigner]] = Def.task {
    new CommandLineGpgSigner(
      gpgCommand.value,
      useGpgAgent.value,
      pgpKeyRing.value,
      pgpSigningKey.value,
      pgpSelectPassphrase.value
    )
  }

  /** Helper to initialize the GPG PgpSigner with Pinentry */
  private[this] def gpgPinEntrySigner: Def.Initialize[Task[PgpSigner]] = Def.task {
    new CommandLineGpgPinentrySigner(
      gpgCommand.value,
      useGpgAgent.value,
      pgpKeyRing.value,
      pgpSigningKey.value,
      pgpSelectPassphrase.value
    )
  }

  /** Helper to initialize the BC PgpVerifier */
  private[this] def bcPgpVerifierFactory: Def.Initialize[Task[PgpVerifierFactory]] = Def.task {
    new BouncyCastlePgpVerifierFactory(pgpCmdContext.value)
  }

  /** Helper to initialize the GPG PgpVerifier */
  private[this] def gpgVerifierFactory: Def.Initialize[Task[PgpVerifierFactory]] = Def.task {
    new CommandLineGpgVerifierFactory(gpgCommand.value, pgpCmdContext.value)
  }

  /** These are all the configuration related settings that are common
   * for a multi-project build, and can be re-used on
   * ThisBuild or maybe Global.
   */
  lazy val signVerifyConfigurationSettings: Seq[Setting[?]] = Seq(
    // TODO - move these to the signArtifactSettings?
    (pgpSigner / skip) := Def.uncached(((pgpSigner / skip) ?? false).value),
    pgpSigner := switch(useGpg, switch(useGpgPinentry, gpgPinEntrySigner, gpgSigner), bcPgpSigner).value,
    pgpVerifierFactory := switch(useGpg, gpgVerifierFactory, bcPgpVerifierFactory).value
  )

  /** Configuration for signing artifacts.  If you use new scopes for
   * packagedArtifacts, you need to add this in that scope to your build.
   *
   * Right now, this also adds duplicate "publish" tasks that will ensure signed
   * artifacts.   While this isn't as friendly to other plugins that want to
   * use our signed artifacts in normal publish flow, it should be more user friendly.
   */
  lazy val signingSettings: Seq[Setting[?]] = signingSettings0 ++ Seq(
    pgpMakeIvy := (Def.taskDyn {
      val style = publishMavenStyle.value
      if (style) Def.task { (None: Option[File]) } else Def.task { Option(deliver.value) }
    }).value,
    publishSignedConfiguration := {
      val _ = pgpMakeIvy.value
      implicit val conv: xsbti.FileConverter = fileConverter.value
      Classpaths.publishConfig(
        publishMavenStyle.value,
        deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        PgpKeys.signedArtifacts.value.toVector.map {
          case (a, x) =>
            a -> toFile(x)
        },
        checksums = (publish / checksums).value.toVector,
        resolverName = Classpaths.getPublishTo(publishTo.value).name,
        logging = ivyLoggingLevel.value,
        overwrite = publishConfiguration.value.overwrite
      )
    },
    publishSigned := publishSignedTask(publishSignedConfiguration, deliver).value,
    publishLocalSignedConfiguration := {
      val _ = deliverLocal.value
      implicit val conv: xsbti.FileConverter = fileConverter.value
      Classpaths.publishConfig(
        publishMavenStyle.value,
        deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        PgpKeys.signedArtifacts.value.toVector.map {
          case (a, x) =>
            a -> toFile(x)
        },
        (publishLocal / checksums).value.toVector,
        resolverName = "local",
        logging = ivyLoggingLevel.value,
        overwrite = publishConfiguration.value.overwrite
      )
    },
    publishLocalSigned := publishSignedTask(publishLocalSignedConfiguration, deliver).value
  )

  def publishSignedTask(config: TaskKey[PublishConfiguration], deliverKey: TaskKey[?]): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      val s = streams.value
      val ref = thisProjectRef.value
      val skp = ((publish / skip) ?? false).value
      if (skp) Def.task { s.log.debug(s"Skipping publishSigned for ${ref.project}") } else
        Classpaths.publishTask(config, deliver)
    }

  /** Settings used to verify signatures on dependent artifacts. */
  lazy val verifySettings: Seq[Setting[?]] = Seq(
    // TODO - This is checking SBT and its plugins signatures..., maybe we can have this be a separate config or something.
    /*signaturesModule in updateClassifiers <<= (projectID, sbtDependency, loadedBuild, thisProjectRef) map { ( pid, sbtDep, lb, ref) =>
      val pluginIDs: Seq[ModuleID] = lb.units(ref.build).unit.plugins.fullClasspath.flatMap(_ get moduleID.key)
      GetSignaturesModule(pid, sbtDep +: pluginIDs, Configurations.Default :: Nil)
    },*/
    (updatePgpSignatures / signaturesModule) := {
      GetSignaturesModule(projectID.value, libraryDependencies.value, Configurations.Default :: Nil)
    },
    updatePgpSignatures := {
      PgpSignatureCheck.resolveSignatures(
        ivySbt.value,
        GetSignaturesConfiguration(
          (updatePgpSignatures / signaturesModule).value,
          updateConfiguration.value,
          ivyScala.value
        ),
        streams.value.log
      )
    },
    checkPgpSignatures := {
      PgpSignatureCheck.checkSignaturesTask(updatePgpSignatures.value, pgpVerifierFactory.value, streams.value)
    }
  )
}
