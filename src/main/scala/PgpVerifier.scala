package com.jsuereth
package pgp

import sbt._
import Keys._

trait PgpVerifier {
  def verifySignature(signatureFile: File, s: TaskStreams): Boolean
}

class CommandLineGpgVerifier(command: String) extends PgpVerifier {
  def verifySignature(signature: File, s: TaskStreams): Boolean = 
    Process(command, Seq("--verify", signature.getAbsolutePath)) ! s.log match {
      case 0 => true
      case n => false
    }
  override def toString = "GPG"
}

class BouncyCastlePgpVerifier(publicKeyRingFile: File) extends PgpVerifier {
  // TODO - Figure out how to auto-pull keys from a server.
  private[this] val ring = PGP loadPublicKeyRing publicKeyRingFile

  def verifySignature(signature: File, s: TaskStreams): Boolean = {
    val original = file(signature.getAbsolutePath.dropRight(gpgExtension.length))
    ring.verifySignatureFile(original, signature)
  }

  override def toString = "BC("+ring+")"
}
