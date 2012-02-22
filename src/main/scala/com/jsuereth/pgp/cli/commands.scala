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
     EncryptFile.parser(ctx) |
     SignKey.parser(ctx) |
     ExportPublicKey.parser(ctx))
}
