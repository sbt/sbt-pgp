package com.jsuereth.sbtpgp

import scala.sys.process.ProcessLogger
import sbt._
import Keys._
import com.jsuereth.pgp.cli.PgpCommandContext

/** The interface used to sign plugins. */
trait PgpSigner {

  /** Signs a given file and writes the output to the signature file specified.
   * Returns the signature file, throws on errors.
   */
  def sign(file: File, signatureFile: File, s: TaskStreams): File
}

object PgpSigner {
  // This is used to synchronize signing to work around
  // https://github.com/sbt/sbt-pgp/issues/168
  private[sbtpgp] val lock = new Object

  private[sbtpgp] def pathStr(file: File): String =
    if (!isWindows) file.getPath
    else
      sys.env.get("GITHUB_ACTIONS") match {
        case Some(_) => file.getPath.replace("\\", "/").replace("C:", "/c")
        case _       => file.getPath
      }
}

/** A GpgSigner that uses the command-line to run gpg. */
class CommandLineGpgSigner(
    command: String,
    agent: Boolean,
    optRing: Option[File],
    optKey: Option[String],
    optPassphrase: Option[Array[Char]]
) extends PgpSigner {
  lazy val gpgVersion: String = {
    import sbt.sbtpgp.Compat._ // needed for sbt 0.13
    val lines = sys.process.Process(command, List("--version")).!!.linesIterator.toList
    lines.headOption match {
      case Some(head) => head.split(" ").last
      case _          => "0.0.0"
    }
  }
  private val TaggedVersion = """(\d{1,14})([\.\d{1,14}]*)((?:-\w+)*)((?:\+.+)*)""".r
  lazy val gpgVersionNumber: (Long, Long) = gpgVersion match {
    case TaggedVersion(m, ns, ts, es) =>
      // null safe, empty string safe
      def splitOn[A](s: String, sep: Char): Vector[String] =
        if (s eq null) Vector()
        else s.split(sep).filterNot(_ == "").toVector
      def splitDot(s: String) = splitOn(s, '.') map (_.toLong)
      (m.toLong, splitDot(ns).headOption.getOrElse(0L))
    case _ => (0L, 0L)
  }
  def isLegacyGpg: Boolean = (gpgVersionNumber._1 < 2L) || (gpgVersionNumber._1 == 2L && gpgVersionNumber._2 == 0L)
  def sign(file: File, signatureFile: File, s: TaskStreams): File = PgpSigner.lock.synchronized {
    if (signatureFile.exists) IO.delete(signatureFile)
    val passargs: Seq[String] = (optPassphrase map { passArray =>
      passArray mkString ""
    } map { pass =>
      // https://github.com/sbt/sbt-pgp/issues/173
      // https://www.gnupg.org/documentation/manuals/gnupg/GPG-Esoteric-Options.html#GPG-Esoteric-Options
      // --passphrase
      // Since Version 2.1 the --pinentry-mode also needs to be set to loopback.
      if (isLegacyGpg) Seq("--batch", "--passphrase", pass)
      else Seq("--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase", pass)
    }) getOrElse Seq.empty
    val ringargs: Seq[String] =
      optRing match {
        case Some(ring) => Seq("--no-default-keyring", "--keyring", PgpSigner.pathStr(ring))
        case _          => Vector.empty
      }
    val keyargs: Seq[String] = optKey map (k => Seq("--default-key", k)) getOrElse Seq.empty
    val args = passargs ++ ringargs ++ Seq("--detach-sign", "--armor") ++ (if (agent) Seq("--use-agent") else Seq.empty) ++ keyargs
    val allArguments: Seq[String] = args ++ Seq("--output", signatureFile.getAbsolutePath, file.getAbsolutePath)
    import sbt.sbtpgp.Compat._ // needed for sbt 0.13
    sys.process.Process(command, allArguments) ! ProcessLogger(s.log.info(_)) match {
      case 0 => ()
      case n => sys.error(s"Failure running '${command + " " + allArguments.mkString(" ")}'.  Exit code: " + n)
    }
    signatureFile
  }

  override val toString: String = "GPG-Command(" + command + ")"
}

/**
 * A GpgSigner that uses the command-line to run gpg with a GPG smartcard.
 *
 * Yubikey 4 has OpenPGP support: https://developers.yubico.com/PGP/ so we can call
 * it directly, and the secret key resides on the card.  This means we need pinentry
 * to be used, and there is no secret key ring.
 */
class CommandLineGpgPinentrySigner(
    command: String,
    agent: Boolean,
    optRing: Option[File],
    optKey: Option[String],
    optPassphrase: Option[Array[Char]]
) extends PgpSigner {
  def sign(file: File, signatureFile: File, s: TaskStreams): File = PgpSigner.lock.synchronized {
    if (signatureFile.exists) IO.delete(signatureFile)
    // (the PIN code is the passphrase)
    // https://wiki.archlinux.org/index.php/GnuPG#Unattended_passphrase
    val pinentryargs: Seq[String] = Seq("--pinentry-mode", "loopback")
    val passargs: Seq[String] = (optPassphrase map { passArray =>
      passArray mkString ""
    } map { pass =>
      Seq("--batch", "--passphrase", pass)
    }) getOrElse Seq.empty
    val ringargs: Seq[String] =
      optRing match {
        case Some(ring) => Seq("--no-default-keyring", "--keyring", PgpSigner.pathStr(ring))
        case _          => Vector.empty
      }
    val keyargs: Seq[String] = optKey map (k => Seq("--default-key", k)) getOrElse Seq.empty
    val args = passargs ++ ringargs ++ pinentryargs ++ Seq("--detach-sign", "--armor") ++ (if (agent) Seq("--use-agent")
                                                                                           else Seq.empty) ++ keyargs
    val allArguments: Seq[String] = args ++ Seq("--output", signatureFile.getAbsolutePath, file.getAbsolutePath)
    import sbt.sbtpgp.Compat._ // needed for sbt 0.13
    sys.process.Process(command, allArguments) ! s.log match {
      case 0 => ()
      case n => sys.error(s"Failure running '${command + " " + allArguments.mkString(" ")}'.  Exit code: " + n)
    }
    signatureFile
  }

  override val toString: String = "GPG-Agent-Command(" + command + ")"
}

/** A GpgSigner that uses bouncy castle. */
class BouncyCastlePgpSigner(ctx: PgpCommandContext, optKey: Option[String]) extends PgpSigner {
  import ctx.{ secretKeyRing => secring, withPassphrase }

  val keyId = optKey match {
    case Some(x) => new java.math.BigInteger(x, 16).longValue
    case _       => secring.secretKey.keyID
  }

  def sign(file: File, signatureFile: File, s: TaskStreams): File =
    withPassphrase(keyId) { pw =>
      if (signatureFile.exists) IO.delete(signatureFile)
      if (!signatureFile.getParentFile.exists) IO.createDirectory(signatureFile.getParentFile)
      secring(keyId).sign(file, signatureFile, pw)
    }
  override lazy val toString: String = "BC-PGP(" + secring + ")"
}
