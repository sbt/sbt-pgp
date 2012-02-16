package com.jsuereth.pgp

import sbt._
import sbt.Keys.TaskStreams

case class SbtPgpCommandContext(
    publicKeyRingFile: File,
    secretKeyRingFile: File,
    optPassphrase: Option[Array[Char]],
    s: TaskStreams
  ) extends cli.PgpCommandContext {
  
  
  def readInput(msg: String): String = System.out.synchronized {
    SimpleReader.readLine(msg) getOrElse sys.error("Failed to grab input")
  }
  def readHidden(msg: String): String = System.out.synchronized {
    SimpleReader.readLine(msg, Some('*')) getOrElse sys.error("Failed to grab input")
  }
  def getPassphrase = optPassphrase getOrElse {
    readHidden("Please enter PGP passphrase: ").toCharArray
  }
  
  def log = s.log
}