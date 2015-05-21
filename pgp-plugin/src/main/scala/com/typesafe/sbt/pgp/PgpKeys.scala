package com.typesafe.sbt
package pgp


import sbt._
import sbt.Keys._
import com.jsuereth.pgp._
import KeyRanks._

/** SBT Keys for the PGP plugin. */
object PgpKeys {
   // PGP related setup
  val pgpSigner     = TaskKey[PgpSigner]("pgp-signer", "The helper class to run GPG commands.", CTask)
  val pgpVerifier   = TaskKey[PgpVerifier]("pgp-verifier", "The helper class to verify public keys from a public key ring.", CTask)
  val pgpSecretRing = SettingKey[File]("pgp-secret-ring", "The location of the secret key ring.  Only needed if using bouncy castle.", ASetting)
  val pgpPublicRing = SettingKey[File]("pgp-public-ring", "The location of the secret key ring.  Only needed if using bouncy castle.", ASetting)
  val pgpPassphrase = SettingKey[Option[Array[Char]]]("pgp-passphrase", "The passphrase associated with the secret used to sign artifacts.", BSetting)
  val pgpSelectPassphrase = TaskKey[Option[Array[Char]]]("pgp-select-passphrase", "The passphrase associated with the secret used to sign artifacts.", CTask)
  val pgpSigningKey = SettingKey[Option[Long]]("pgp-signing-key", "The key used to sign artifacts in this project.  Must be the full key id (not just lower 32 bits).", BSetting)
  
  // PGP Related tasks  (TODO - make these commands?)
  val pgpReadOnly = SettingKey[Boolean]("pgp-read-only", "If set to true, the PGP usage will not modify any public/private keyrings.", CSetting)
  val pgpCmd = InputKey[Unit]("pgp-cmd", "Runs one of the various PGP commands.", ATask)
  val pgpStaticContext = SettingKey[cli.PgpStaticContext]("pgp-static-context", "Context used for auto-completing PGP commands.", CSetting)
  val pgpCmdContext = TaskKey[cli.PgpCommandContext]("pgp-context", "Context used to run PGP commands.", CTask)
  
  // GPG Related Options
  val gpgCommand = SettingKey[String]("gpg-command", "The path of the GPG command to run", BSetting)
  val useGpg = SettingKey[Boolean]("use-gpg", "If this is set to true, the GPG command line will be used.", ASetting)
  val useGpgAgent = SettingKey[Boolean]("use-gpg-agent", "If this is set to true, the GPG command line will expect a GPG agent for the password.", BSetting)
  
  // Checking PGP Signatures options
  val signaturesModule = TaskKey[GetSignaturesModule]("signatures-module", "", CTask)
  val updatePgpSignatures = TaskKey[UpdateReport]("update-pgp-signatures", "Resolves and optionally retrieves signatures for artifacts, transitively.", CTask)
  val checkPgpSignatures = TaskKey[SignatureCheckReport]("check-pgp-signatures", "Checks the signatures of artifacts to see if they are trusted.", APlusTask)
  
  // Publishing settings
  val publishSignedConfiguration = TaskKey[PublishConfiguration]("publish-signed-configuration", "Configuration for publishing to a repository.", DTask)
  val publishLocalSignedConfiguration = TaskKey[PublishConfiguration]("publish-local-signed-configuration", "Configuration for publishing to the local repository.", DTask)
  val signedArtifacts = TaskKey[Map[Artifact,File]]("signed-artifacts", "Packages all artifacts for publishing and maps the Artifact definition to the generated file.", CTask)
  val publishSigned = TaskKey[Unit]("publish-signed", "Publishing all artifacts, but SIGNED using PGP.", APlusTask)
  val publishLocalSigned = TaskKey[Unit]("publish-local-signed", "Publishing all artifacts to a local repository, but SIGNED using PGP.", APlusTask)
}