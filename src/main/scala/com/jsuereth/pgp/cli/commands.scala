package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

/** Represents a PgpCommand */
trait PgpCommand {
  def run(ctx: PgpCommandContext): Unit
  /** Returns true if the command will not modify the public/private keys. */
  def isReadOnly: Boolean = false
}
object PgpCommand {
  def parser(ctx: PgpStaticContext): Parser[PgpCommand] =
    (GeneratePgpKey.parser(ctx) |
     ListKeys.parser(ctx) |
     ListSigs.parser(ctx) |
     SendKey.parser(ctx) |
     ReceiveKey.parser(ctx) |
     ImportKey.parser(ctx) |
     EncryptMessage.parser(ctx) |
     SignKey.parser(ctx) |
     ExportPublicKey.parser(ctx))
}

case class EncryptFile(file: File, pubKey: String) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (for {
      keyring <- ctx.publicKeyRing findPubKeyRing pubKey
      encKey <- keyring.encryptionKeys.headOption
    } yield encKey) getOrElse sys.error("Could not find encryption key for: " + pubKey)
    key.encryptFile(file, new File(file.getAbsolutePath + ".asc"))
  }
  override def isReadOnly: Boolean = true
}
object EncryptFile {
  def parser(ctx: PgpStaticContext): Parser[EncryptFile] = null
}

case class EncryptMessage(msg: String, pubKey: String) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (for {
      keyring <- ctx.publicKeyRing findPubKeyRing pubKey
      encKey <- keyring.encryptionKeys.headOption
    } yield encKey) getOrElse sys.error("Could not find encryption key for: " + pubKey)
    ctx.output(key.encryptString(msg))
  }
}
object EncryptMessage {
  def parser(ctx: PgpStaticContext): Parser[EncryptMessage] = {
    // TODO - More robust/better parsing
    (token("encrypt-msg") ~ Space) ~> existingKeyIdOrUser(ctx) ~ (Space ~> message) map {
      case key ~ msg => EncryptMessage(msg, key)
    }
  }  
}
