package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._


case class SignKey(pubKey: String, notation: (String,String)) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val matches = for {
      ring <- ctx.publicKeyRing.keyRings.toSeq
      key <- ring.publicKeys
      if PGP.isPublicKeyMatching(pubKey)(key)
    } yield ring -> key
    ctx.log.info("Found keyring: " + matches)
    val newpubringcol = matches match {
      case Seq((ring, key), _*) =>
        val newkey = ctx withPassphrase { pw => 
          ctx.log.info("Signing key: " + key)
          ctx.secretKeyRing.secretKey.signPublicKey(key, notation, pw)
        }
        ctx.log.info("New key: " + key)
        val newpubring = ring :+ newkey
        ctx.log.info("New pubring: " + newpubring)
        (ctx.publicKeyRing removeRing ring)  :+ newpubring
      case Seq()            => sys.error("Could not find key: " + pubKey)
      case matches          => sys.error("Found more than on pulic key: " + matches.map(_._2).mkString(","))
    }
    ctx.log.info("Saving public ring changes")
    newpubringcol saveToFile ctx.publicKeyRingFile
  }
}
object SignKey {
  def parser(ctx: PgpStaticContext): Parser[PgpCommand] =
    ((token("sign-key") ~ Space) ~> existingKeyIdOrUserOption(ctx) ~ (Space ~> attribute)) map {
      case key ~ attr => SignKey(key, attr)
    }
}