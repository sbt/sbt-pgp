package com.jsuereth
package pgp
package sbtplugin


import sbt._
import Keys._

/** SBT Keys for the PGP plugin. */
object PgpKeys {
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
}