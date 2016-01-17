package com.jsuereth.sbt
package pgp

import sbt._
import sbt.Keys.TaskStreams
import com.jsuereth.pgp._

case class SbtPgpStaticContext(
    publicKeyRingFile: File,
    secretKeyRingFile: File) extends cli.PgpStaticContext

/** Context used by PGP commands as they execute. */
case class SbtPgpCommandContext(
    ctx: cli.PgpStaticContext,
    interaction: sbt.InteractionService,
    optPassphrase: Option[Array[Char]],
    s: TaskStreams
  ) extends cli.PgpCommandContext with cli.DelegatingPgpStaticContext {

  // For binary compatibility
  def this(ctx: cli.PgpStaticContext,
           optPassphrase: Option[Array[Char]],
           s: TaskStreams) = this(ctx, HackInteractionAccess.defaultInteraction, optPassphrase, s)

  def readInput(msg: String): String = System.out.synchronized {
    interaction.readLine(msg, mask=false) getOrElse sys.error("Failed to grab input")
  }
  def readHidden(msg: String): String = System.out.synchronized {
    interaction.readLine(msg, mask=true) getOrElse sys.error("Failed to grab input")
  }
  def inputPassphrase = readHidden("Please enter PGP passphrase (or ENTER to abort): ") match {
    case s: String if !s.isEmpty => s.toCharArray
    case _ => sys.error("Empty passphrase. aborting...")
  }

  def withPassphrase[U](f: Array[Char] => U): U = {
    retry[U, IncorrectPassphraseException](3){
      PasswordCache.withValue(
        key = ctx.secretKeyRingFile.getAbsolutePath,
        default = optPassphrase getOrElse inputPassphrase)(f)
    } match {
      case Right(u) => u
      case Left(e) => sys.error("Wrong passphrase. aborting...")
    }
  }

  private def retry[A, E <: Exception](n: Int)(body: => A)(implicit desired: scala.reflect.ClassTag[E]): Either[E, A] =
    try Right(body)
    catch {
      case e: Exception if (desired.runtimeClass isAssignableFrom e.getClass) =>
        if (n <= 1) Left(e.asInstanceOf[E]) else retry[A, E](n - 1)(body)
    }

  def log = s.log
  // TODO - Is this the right thing to do?
  def output[A](msg: => A): Unit = println(msg)
}
