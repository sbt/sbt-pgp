package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

case class ImportKey(pubKey: File) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = PGP loadPublicKeyRing pubKey
    ctx addPublicKeyRing key
  }
}
object ImportKey {
  def parser(ctx: PgpStaticContext): Parser[ImportKey] = {
    (token("import-pub-key") ~ Space) ~> filename map ImportKey.apply
  }
}