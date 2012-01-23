package com.jsuereth
package pgp
package sbtplugin

import sbt._
import Keys._

/** The interface used to sign plugins. */
trait PgpSigner {
  /** Signs a given file and writes the output to the signature file specified.  
   * Returns the signature file, throws on errors. 
   */
  def sign(file: File, signatureFile: File, s: TaskStreams): File
  /** Generates a new PGP key at the given locations.
   * @param pubKey the location for the public keystore.
   * @param secKey the lcoation for the secret keystore.
   * @param identity The PGP identity for the key.
   * @param s The TaskStreams logger to use.
   */
  def generateKey(pubKey: File, secKey: File, identity: String, s: TaskStreams): Unit
}

/** A GpgSigner that uses the command-line to run gpg. */
class CommandLineGpgSigner(command: String, agent: Boolean) extends PgpSigner {
  def sign(file: File, signatureFile: File, s: TaskStreams): File = {
      if (signatureFile.exists) IO.delete(signatureFile)
      val args = Seq("--detach-sign", "--armor") ++ (if(agent) Seq("-use-agent") else Seq.empty)
       Process(command, args ++ Seq("--output", signatureFile.getAbsolutePath, file.getAbsolutePath)) ! s.log match {
         case 0 => ()
         case n => sys.error("Failure running gpg --detach-sign.  Exit code: " + n)
       }
       signatureFile 
  }
  def generateKey(pubKey: File, secKey: File, identity: String, s: TaskStreams): Unit = 
    Process(command, Seq("--gen-key")) ! s.log match {
      case 0 => ()
      case n => sys.error("Trouble running gpg --gen-key!")
    }

  override val toString = "GPG-Command(" + command + ")"
}
/** A GpgSigner that uses bouncy castle. */
class BouncyCastlePgpSigner(secretKeyRingFile: File, passPhrase: Array[Char]) extends PgpSigner {
  lazy val secring = PGP.loadSecretKeyRing(secretKeyRingFile)
  def sign(file: File, signatureFile: File, s: TaskStreams): File = {
    if (signatureFile.exists) IO.delete(signatureFile)
    if (!signatureFile.getParentFile.exists) IO.createDirectory(signatureFile.getParentFile)
    secring.secretKey.sign(file, signatureFile, passPhrase)
  }
  def generateKey(pubKey: File, secKey: File, identity: String, s: TaskStreams): Unit = {
    if(!pubKey.getParentFile.exists) IO.createDirectory(pubKey.getParentFile)
    if(!secKey.getParentFile.exists) IO.createDirectory(secKey.getParentFile)
    s.log.info("Creating a new PGP key.   This could take a long time to gather enough random bits for entropy.")
    PGP.makeKeys(identity, passPhrase, pubKey, secKey)
    s.log.info("Public key := " + pubKey.getAbsolutePath)
    s.log.info("Secret key := " + secKey.getAbsolutePath)
    s.log.info("Please do not share your secret key.   Your public key is free to share.")
  }
  override val toString = "BC-PGP(" + secring + ")"
}
