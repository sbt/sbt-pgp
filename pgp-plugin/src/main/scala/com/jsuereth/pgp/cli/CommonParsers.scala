package com.jsuereth.pgp.cli

import sbt._
import complete._
import DefaultParsers._

object CommonParsers {
  lazy val hexaDigit = chars("ABCDEFGabcdefg0123456789")
  lazy val hexanum = hexaDigit.+ map { _ mkString "" }
  lazy val keyId = token(hexanum, "<keyid>") map (java.lang.Long.parseLong(_, 16))
  
  private def hexPublicKeyIds(ctx: PgpStaticContext): Seq[String] =
    try {
      ctx.publicKeyRing.publicKeys.view map (_.keyID) map ("%x" format (_)) toSeq
    } catch {
      case _ => Seq.empty
    }
  /** Parser for existing public key ids. */
  def existingPublicKeyId(ctx: PgpStaticContext) = 
    token(hexanum, "<keyid>").examples(hexPublicKeyIds(ctx):_*) map (java.lang.Long.parseLong(_, 16))
  
  lazy val keyIdOrUser: Parser[String] = token(NotSpace, "<key id/user>")
  
  private def userIds(ctx: PgpStaticContext): Seq[String] =
    try {
      ctx.publicKeyRing.publicKeys.view flatMap (_.userIDs) toSeq
    } catch {
      case _ => Seq.empty
    }
  
  def existingKeyIdOrUser(ctx: PgpStaticContext): Parser[String] =
    keyIdOrUser.examples((userIds(ctx) ++ hexPublicKeyIds(ctx)):_*)
  // TODO - ensure urls are urls
  lazy val hkpUrl = token(NotSpace, "<hkp server url>")
  
  lazy val keyIdOption = token("keyId=") ~> keyId
  lazy val keyIdOrUserOption = token("key=") ~> keyIdOrUser
  def existingKeyIdOrUserOption(ctx: PgpStaticContext) = 
    token("key=") ~> existingKeyIdOrUser(ctx)
  lazy val attribute: Parser[(String,String)] = {
    val name = token(NotSpace, "<attribute name>")
    val value = token(NotSpace, "<attribute value>")
    (name ~ ((Space.? ~ "->" ~ Space.?) ~> value)) map { case k ~ v => k -> v }
  }
  lazy val message = token(("message" ~  "=" ~ "\"") map { case _ => () }) ~> (any & not('"')).+.string <~ '"'
  // TODO - better base directory
  lazy val filename = fileParser(new java.io.File("."))
}