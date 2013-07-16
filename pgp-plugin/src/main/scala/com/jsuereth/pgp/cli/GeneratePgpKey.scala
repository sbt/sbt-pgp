package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

/** Constructs a new PGP key from user input. */
case class GeneratePgpKey() extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    import ctx.{publicKeyRingFile=>pub,secretKeyRingFile=>sec, log}
    if(pub.exists) sys.error("Public key ring (" + pub.getAbsolutePath + ") already exists!")
    if(sec.exists) sys.error("Secret key ring (" + sec.getAbsolutePath + ") already exists!")
    if(!pub.getParentFile.exists) IO.createDirectory(pub.getParentFile)
    if(!sec.getParentFile.exists) IO.createDirectory(sec.getParentFile)
    val name = ctx.readInput("Please enter the name associated with the key: ")
    val email = ctx.readInput("Please enter the email associated with the key: ")
    val pw = ctx.readHidden("Please enter the passphrase for the key: ")
    val pw2 = ctx.readHidden("Please re-enter the passphrase for the key: ")
    if(pw != pw2) sys.error("Passphrases do not match!")
    val id = "%s <%s>".format(name, email)
    log.info("Creating a new PGP key, this could take a long time.")
    PGP.makeKeys(id, pw.toCharArray, pub, sec)
    log.info("Public key := " + pub.getAbsolutePath)
    log.info("Secret key := " + sec.getAbsolutePath)
    log.info("Please do not share your secret key.   Your public key is free to share.")
  }
}
object GeneratePgpKey {
  def parser(ctx: PgpStaticContext): Parser[GeneratePgpKey] =
    token(("gen-key": Parser[String]) map { _ => GeneratePgpKey() })
}


