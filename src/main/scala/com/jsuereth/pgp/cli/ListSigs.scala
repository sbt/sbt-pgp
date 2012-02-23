package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

/** Lists Signatures on a file. */
case class ListSigs() extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    import Display._
    ctx.log.info("Looking for sigs")
    ctx output {
      printFileHeader(ctx.publicKeyRingFile) + 
      (ctx.publicKeyRing.keyRings map printRingWithSignatures mkString "\n")  
    }
  }
  override def isReadOnly = true
}
object ListSigs {
  def parser(ctx: PgpStaticContext): Parser[PgpCommand] =
    token("list-sigs") map { _ => ListSigs() }
}