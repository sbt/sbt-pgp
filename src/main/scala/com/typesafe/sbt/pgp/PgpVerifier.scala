package com.typesafe.sbt
package pgp

import scala.util.matching.Regex
import scala.util.control.Exception._
import sbt._
import Keys._
import com.jsuereth.pgp.cli.PgpCommandContext
import com.jsuereth.pgp.KeyNotFoundException

trait PgpVerifier {
  def verifySignature(signatureFile: File, s: TaskStreams): SignatureCheckResult
}

/** Matcher for an untrusted key. */
object UntrustedKey {
  val Pattern = new Regex(".*gpg:.*Signature.*key\\s+ID\\s+([A-F0-9]+).*public\\skey\\snot\\sfound.*")
  
  def unapply(content: String): Option[Long] = 
    content match {
      case Pattern(id) => catching(classOf[NumberFormatException]) opt (java.lang.Long.parseLong(id, 16))
      case _           => None
    }
}

/** Helper class to grab all the output from a process into one string. */
class ProcessGrabber extends ProcessLogger {
  private[this] val sb = new StringBuilder
  
  def buffer [T] (f: ⇒ T): T = f
  def error (s: ⇒ String): Unit = sb append s
  def info (s: ⇒ String): Unit = sb append s
  
  def result = sb.toString
}

class CommandLineGpgVerifier(command: String) extends PgpVerifier {
  def verifySignature(signature: File, s: TaskStreams): SignatureCheckResult = {
    val grabber = new ProcessGrabber
    (Process(command, Seq("--verify", signature.getAbsolutePath)) ! grabber, grabber.result) match {
      case (0, _)                 => SignatureCheckResult.OK
      case (_, UntrustedKey(key)) => SignatureCheckResult.UNTRUSTED(key)
      case (n, _)                 => SignatureCheckResult.BAD
    }
  }
  override def toString = "GPG"
}

class BouncyCastlePgpVerifier(ctx: PgpCommandContext) extends PgpVerifier {
  // TODO - Figure out how to auto-pull keys from a server.
  import ctx.{publicKeyRing => ring}

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
