package com.jsuereth.pgp
package sbtplugin

import sbt._
import sbt.Keys.TaskStreams

case class SbtPgpStaticContext(
    publicKeyRingFile: File,
    secretKeyRingFile: File) extends cli.PgpStaticContext {
  
}

/** Context used by PGP commands as they execute. */
case class SbtPgpCommandContext(
    ctx: cli.PgpStaticContext,
    optPassphrase: Option[Array[Char]],
    s: TaskStreams
  ) extends cli.PgpCommandContext with cli.DelegatingPgpStaticContext {
  
  def readInput(msg: String): String = System.out.synchronized {
    SimpleReader.readLine(msg) getOrElse sys.error("Failed to grab input")
  }
  def readHidden(msg: String): String = System.out.synchronized {
    SimpleReader.readLine(msg, Some('*')) getOrElse sys.error("Failed to grab input")
  }
  def inputPassphrase = readHidden("Please enter PGP passphrase: ").toCharArray
  def withPassphrase[U](f: Array[Char] => U): U =
    PasswordCache.withValue(
        key = ctx.secretKeyRingFile.getAbsolutePath,
        default = optPassphrase getOrElse inputPassphrase)(f)
  
  def log = s.log
  // TODO - Is this the right thing to do?
  def output[A](msg: => A): Unit = println(msg)
}