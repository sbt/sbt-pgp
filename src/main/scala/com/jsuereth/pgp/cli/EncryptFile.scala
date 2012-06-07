package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

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
  def parser(ctx: PgpStaticContext): Parser[PgpCommand] = {
    (token("encrypt-msg") ~ Space) ~> existingKeyIdOrUser(ctx) ~ (Space ~> filename) map {
      case key ~ file => EncryptFile(file, key)
    }
  }
}