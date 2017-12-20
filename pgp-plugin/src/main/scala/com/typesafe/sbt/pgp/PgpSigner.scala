package com.typesafe.sbt
package pgp

import sbt._
import Keys._
import com.jsuereth.pgp.cli.PgpCommandContext
import sbt.sbtpgp.Compat._

/** The interface used to sign plugins. */
trait PgpSigner {
  /** Signs a given file and writes the output to the signature file specified.  
   * Returns the signature file, throws on errors. 
   */
  def sign(file: File, signatureFile: File, s: TaskStreams): File
}

/** A GpgSigner that uses the command-line to run gpg. */
class CommandLineGpgSigner(command: String, agent: Boolean, optKey: Option[Long]) extends PgpSigner {
  def sign(file: File, signatureFile: File, s: TaskStreams): File = {
    if (signatureFile.exists) IO.delete(signatureFile)
    val keyargs: Seq[String] = optKey map (k => Seq("--default-key", "0x%x" format(k))) getOrElse Seq.empty
    val args = Seq("--detach-sign", "--armor") ++ (if(agent) Seq("--use-agent") else Seq.empty) ++ keyargs
    sys.process.Process(command, args ++ Seq("--output", signatureFile.getAbsolutePath, file.getAbsolutePath)) ! s.log match {
      case 0 => ()
      case n => sys.error("Failure running gpg --detach-sign.  Exit code: " + n)
    }
    signatureFile
  }

  override val toString = "GPG-Command(" + command + ")"
}
/** A GpgSigner that uses bouncy castle. */
class BouncyCastlePgpSigner(ctx: PgpCommandContext, optKey: Option[Long]) extends PgpSigner {
  import ctx.{secretKeyRing => secring, withPassphrase}
  val keyId = optKey.getOrElse(secring.secretKey.keyID)
  
  def sign(file: File, signatureFile: File, s: TaskStreams): File = 
    withPassphrase(keyId) { pw =>
      if (signatureFile.exists) IO.delete(signatureFile)
      if (!signatureFile.getParentFile.exists) IO.createDirectory(signatureFile.getParentFile)
      secring(keyId).sign(file, signatureFile, pw)
    }
  override lazy val toString = "BC-PGP(" + secring + ")"
}
