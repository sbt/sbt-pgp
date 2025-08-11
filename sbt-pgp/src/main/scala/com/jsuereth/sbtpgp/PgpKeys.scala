package com.jsuereth.sbtpgp

import sbt._
import com.jsuereth.pgp._
import sbt.sbtpgp.Compat, Compat._
// import sbt.util.cacheLevel

/** SBT Keys for the PGP plugin. */
object PgpKeys {
  // PGP related setup
  @cacheLevel(include = Array.empty)
  val pgpSigner = taskKey[PgpSigner]("The helper class to run gpg commands.")

  @cacheLevel(include = Array.empty)
  val pgpVerifierFactory = taskKey[PgpVerifierFactory]("The helper class to verify public keys from a public key ring.")

  @cacheLevel(include = Array.empty)
  val pgpKeyRing = settingKey[Option[File]](
    "The location of the key ring, passed to gpg command as --no-default-keyring --keyring <value>."
  )

  @cacheLevel(include = Array.empty)
  val pgpSecretRing = settingKey[File]("The location of the secret key ring. Only needed if using Bouncy Castle.")

  @cacheLevel(include = Array.empty)
  val pgpPublicRing = settingKey[File]("The location of the secret key ring. Only needed if using Bouncy Castle.")

  @cacheLevel(include = Array.empty)
  val pgpPassphrase =
    settingKey[Option[Array[Char]]]("The passphrase associated with the secret used to sign artifacts.")

  @cacheLevel(include = Array.empty)
  val pgpSelectPassphrase =
    taskKey[Option[Array[Char]]]("The passphrase associated with the secret used to sign artifacts.")

  @cacheLevel(include = Array.empty)
  val pgpSigningKey = taskKey[Option[String]](
    "The key used to sign artifacts in this project, passed to gpg command as --default-key <value>."
  )

  // PGP Related tasks  (TODO - make these commands?)
  val pgpStaticContext = settingKey[cli.PgpStaticContext]("Context used for auto-completing PGP commands.")

  @cacheLevel(include = Array.empty)
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
  @cacheLevel(include = Array.empty)
  val signaturesModule = taskKey[GetSignaturesModule]("")

  @cacheLevel(include = Array.empty)
  val updatePgpSignatures =
    taskKey[UpdateReport]("Resolves and optionally retrieves signatures for artifacts, transitively.")

  @cacheLevel(include = Array.empty)
  val checkPgpSignatures =
    taskKey[SignatureCheckReport]("Checks the signatures of artifacts to see if they are trusted.")

  // Publishing settings
  @cacheLevel(include = Array.empty)
  val publishSignedConfiguration = taskKey[PublishConfiguration]("Configuration for publishing to a repository.")

  @cacheLevel(include = Array.empty)
  val publishLocalSignedConfiguration =
    taskKey[PublishConfiguration]("Configuration for publishing to the local repository.")
  val signedArtifacts = Compat.signedArtifacts

  @cacheLevel(include = Array.empty)
  val publishSigned = taskKey[Unit]("Publishing all artifacts, but SIGNED using PGP.")

  @cacheLevel(include = Array.empty)
  val publishLocalSigned = taskKey[Unit]("Publishing all artifacts to a local repository, but SIGNED using PGP.")

  @cacheLevel(include = Array.empty)
  val pgpMakeIvy = taskKey[Option[File]]("Generates the Ivy file.")
}
