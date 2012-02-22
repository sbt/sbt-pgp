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

case class ImportKey(pubKey: File) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = ((PGP loadPublicKeyRing pubKey).publicKeys.headOption
               getOrElse sys.error("Could not find a public key in: " + pubKey))

    ctx addPublicKey key
  }
}
object ImportKey {
  def parser(ctx: PgpStaticContext): Parser[ImportKey] = {
    (token("import-pub-key") ~ Space) ~> filename map ImportKey.apply
  }
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
