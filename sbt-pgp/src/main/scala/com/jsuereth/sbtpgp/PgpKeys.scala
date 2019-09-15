package com.jsuereth.sbtpgp

import sbt._
import com.jsuereth.pgp._
import sbt.sbtpgp.Compat._

/** SBT Keys for the PGP plugin. */
object PgpKeys {
  // PGP related setup
  val pgpSigner = taskKey[PgpSigner]("The helper class to run gpg commands.")
  val pgpVerifierFactory = taskKey[PgpVerifierFactory]("The helper class to verify public keys from a public key ring.")
  val pgpKeyRing = settingKey[Option[File]](
    "The location of the key ring, passed to gpg command as --no-default-keyring --keyring <value>."
  )
  val pgpSecretRing = settingKey[File]("The location of the secret key ring. Only needed if using Bouncy Castle.")
  val pgpPublicRing = settingKey[File]("The location of the secret key ring. Only needed if using Bouncy Castle.")
  val pgpPassphrase =
    settingKey[Option[Array[Char]]]("The passphrase associated with the secret used to sign artifacts.")
  val pgpSelectPassphrase =
    taskKey[Option[Array[Char]]]("The passphrase associated with the secret used to sign artifacts.")
  val pgpSigningKey = taskKey[Option[String]](
    "The key used to sign artifacts in this project, passed to gpg command as --default-key <value>."
  )

  // PGP Related tasks  (TODO - make these commands?)
  val pgpStaticContext = settingKey[cli.PgpStaticContext]("Context used for auto-completing PGP commands.")
  val pgpCmdContext = taskKey[cli.PgpCommandContext]("Context used to run PGP commands.")

  // GPG Related Options
  val gpgCommand = settingKey[String]("The path of the GPG command to run")
  val useGpg = settingKey[Boolean]("If this is set to true, the GPG command line will be used.")
  val useGpgAgent =
    settingKey[Boolean]("If this is set to true, the GPG command line will expect a GPG agent for the password.")
  val useGpgPinentry = settingKey[Boolean](
    "If this is set to true, the GPG command line will expect pinentry will be used with gpg-agent."
  )

  // Checking PGP Signatures options
  val signaturesModule = taskKey[GetSignaturesModule]("")
  val updatePgpSignatures =
    taskKey[UpdateReport]("Resolves and optionally retrieves signatures for artifacts, transitively.")
  val checkPgpSignatures =
    taskKey[SignatureCheckReport]("Checks the signatures of artifacts to see if they are trusted.")

  // Publishing settings
  val publishSignedConfiguration = taskKey[PublishConfiguration]("Configuration for publishing to a repository.")
  val publishLocalSignedConfiguration =
    taskKey[PublishConfiguration]("Configuration for publishing to the local repository.")
  val signedArtifacts = taskKey[Map[Artifact, File]](
    "Packages all artifacts for publishing and maps the Artifact definition to the generated file."
  )
  val publishSigned = taskKey[Unit]("Publishing all artifacts, but SIGNED using PGP.")
  val publishLocalSigned = taskKey[Unit]("Publishing all artifacts to a local repository, but SIGNED using PGP.")
  val pgpMakeIvy = taskKey[Option[File]]("Generates the Ivy file.")
}
