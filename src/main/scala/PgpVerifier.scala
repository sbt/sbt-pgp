package com.jsuereth
package pgp
package sbtplugin

import sbt._
import Keys._

trait PgpVerifier {
  def verifySignature(signatureFile: File, s: TaskStreams): SignatureCheckResult
}

class CommandLineGpgVerifier(command: String) extends PgpVerifier {
  def verifySignature(signature: File, s: TaskStreams): SignatureCheckResult = 
    Process(command, Seq("--verify", signature.getAbsolutePath)) ! s.log match {
      case 0 => SignatureCheckResult.OK
      case n => SignatureCheckResult.BAD
    }
  override def toString = "GPG"
}

class BouncyCastlePgpVerifier(publicKeyRingFile: File) extends PgpVerifier {
  // TODO - Figure out how to auto-pull keys from a server.
  private[this] val ring = PGP loadPublicKeyRing publicKeyRingFile

  def verifySignature(signature: File, s: TaskStreams): SignatureCheckResult = {
    val original = file(signature.getAbsolutePath.dropRight(gpgExtension.length))
    try {
      if(ring.verifySignatureFile(original, signature))  SignatureCheckResult.OK
      else SignatureCheckResult.BAD
    } catch {
      case KeyNotFoundException(key) => SignatureCheckResult.UNTRUSTED(key)
    }
  }

  override def toString = "BC("+ring+")"
}
