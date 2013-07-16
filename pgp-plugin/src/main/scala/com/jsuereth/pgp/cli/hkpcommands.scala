package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

/** Helper for running HKP protocol commands. */
trait HkpCommand extends PgpCommand {
  def hkpUrl: String
  def hkpClient = hkp.Client(hkpUrl)
}

case class SendKey(pubKey: String, hkpUrl: String) extends HkpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    import ctx.{publicKeyRing => pubring, log}
    val key = pubring findPubKeyRing pubKey getOrElse sys.error("Could not find public key: " + pubKey)
    val client = hkpClient
    log.info("Sending " + key + " to " + client)
    client.pushKeyRing(key, { s: String => log.debug(s) })
  }
  override def isReadOnly: Boolean = true
}

object SendKey {
  def parser(ctx: PgpStaticContext): Parser[SendKey] = {
    (token("send-key") ~ Space) ~> existingKeyIdOrUser(ctx) ~ (Space ~> hkpUrl) map {
      case key ~ url => SendKey(key, url)
    }
  }
}

case class ReceiveKey(pubKeyId: Long, hkpUrl: String) extends HkpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (hkpClient.getKey(pubKeyId) 
        getOrElse sys.error("Could not find key: " + pubKeyId + " on server " + hkpUrl))
    ctx.log.info("Adding public key: " + key)
    // TODO - Remove if key already exists...
    ctx addPublicKeyRing key
  }
}
object ReceiveKey {
  def parser(ctx: PgpStaticContext): Parser[ReceiveKey] = {
    // TODO - More robust...
    (token("recv-key") ~ Space) ~> keyId ~ (Space ~> hkpUrl) map {
      case key ~ url => ReceiveKey(key,url)
    }
  }
}
