package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

case class ExportPublicKey(id: String) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (ctx.publicKeyRing.findPubKeyRing(id) getOrElse
        sys.error("Could not find key: " + id))
    ctx.output(key.saveToString)
  }
  override def isReadOnly: Boolean = true
}
object ExportPublicKey {
  def parser(ctx: PgpStaticContext): Parser[ExportPublicKey] = {
    (token("export-pub-key") ~ Space) ~> existingKeyIdOrUser(ctx) map ExportPublicKey.apply
  }
}