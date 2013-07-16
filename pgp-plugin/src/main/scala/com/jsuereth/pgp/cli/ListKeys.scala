package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

case class ListKeys() extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    import Display._
    ctx output {
      printFileHeader(ctx.publicKeyRingFile) + 
      (ctx.publicKeyRing.keyRings map printRing mkString "\n")  
    }
  }
  override def isReadOnly = true
}
object ListKeys {
  def parser(ctx: PgpStaticContext): Parser[ListKeys] =
    token("list-keys") map { _ => ListKeys() }
}