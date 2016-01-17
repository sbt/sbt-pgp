package com.jsuereth.sbtpgp

import scala.util.matching.Regex
import scala.util.control.Exception._
import scala.sys.process.{ Process, ProcessLogger }
import sbt._
import Keys._
import com.jsuereth.pgp.cli.PgpCommandContext
import com.jsuereth.pgp.KeyNotFoundException

trait PgpVerifierFactory {
  def withVerifier[T](f: PgpVerifier => T): T
}

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
  def buffer[T](f: => T): T = f
  def error(s: => String): Unit = sb append s
  def info(s: => String): Unit = sb append s
  def err(s: => String): Unit = error(s)
  def out(s: => String): Unit = info(s)

  def result = sb.toString
}
class CommandLineGpgVerifierFactory(command: String, ctx: PgpCommandContext)
  extends PgpVerifierFactory {
  override def withVerifier[T](f: PgpVerifier => T): T = {
    IO.withTemporaryDirectory(tempdir => {
      import ctx.{publicKeyRingFile => ringFile}
      val ringPath = ringFile.toString
      val gnuPGPath = tempdir.toString
      val args = Seq("--homedir", gnuPGPath, "--import", ringPath)
      val grabber = new ProcessGrabber
      (Process(command, args) ! grabber, grabber.result) match {
        case (0, _) => f(new CommandLineGpgVerifier(tempdir))
        case _ => f(AlwaysBadGpgVerifier)
      }
    })
  }

  private object AlwaysBadGpgVerifier extends PgpVerifier {
    def verifySignature(s: File, ts: TaskStreams) =
      SignatureCheckResult.BAD
    override def toString = "BAD"
  }

  private class CommandLineGpgVerifier(gnuPGHome: File) extends PgpVerifier {
    def verifySignature(signature: File, s: TaskStreams): SignatureCheckResult = {
      val grabber = new ProcessGrabber
      val gnuPGPath = gnuPGHome.toString
      val args = Seq("--homedir", gnuPGPath, "--verify", signature.getAbsolutePath)
      (Process(command, args) ! grabber, grabber.result) match {
        case (0, _)                 => SignatureCheckResult.OK
        case (_, UntrustedKey(key)) => SignatureCheckResult.UNTRUSTED(key)
        case (n, _)                 => SignatureCheckResult.BAD
      }
    }
    override def toString = "GPG"
  }
}

class BouncyCastlePgpVerifierFactory(ctx: PgpCommandContext) extends PgpVerifierFactory {
  override def withVerifier[T](f: PgpVerifier => T): T =
    f(BouncyCastlePgpVerifier)
  object BouncyCastlePgpVerifier extends PgpVerifier {
    // TODO - Figure out how to auto-pull keys from a server.
    import ctx.{publicKeyRing => ring}

    def verifySignature(signature: File, s: TaskStreams): SignatureCheckResult = {
      val original = file(signature.getAbsolutePath.dropRight(gpgExtension.length))
      try {
        if (ring.verifySignatureFile(original, signature)) SignatureCheckResult.OK
        else SignatureCheckResult.BAD
      } catch {
        case KeyNotFoundException(key) => SignatureCheckResult.UNTRUSTED(key)
      }
    }

    override def toString = "BC(" + ring + ")"
  }

}
