package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._

/** Represents a PgpCommand */
sealed trait PgpCommand {
  def run(ctx: PgpCommandContext): Unit
}
object PgpCommand {
  def parser: Parser[PgpCommand] =
    (GeneratePgpKey.parser |
     SendKey.parser |
     ReceiveKey.parser)
}


/** A context for accepting user input. */
trait UICommandContext {
  /** Displays the message to the user and accepts their input. */
  def readInput(msg: String): String
  /** Displays the message to the user and accepts their input. 
   * Replaces characters entered with '*'.
   */
  def readHidden(msg: String): String
}

/** The context used when running PGP commands. */
trait PgpCommandContext extends UICommandContext {
  def publicKeyRingFile: File
  def secretKeyRingFile: File
  def getPassphrase: Array[Char]
  def log: Logger
  
  // Derived methods
  def publicKeyRing: PublicKeyRing = PGP loadPublicKeyRing publicKeyRingFile
  def secretKeyRing: SecretKeyRing = PGP loadSecretKeyRing secretKeyRingFile
  def addPublicKey(key: PublicKey): Unit = {
    val newring = publicKeyRing :+ key
    newring saveToFile publicKeyRingFile
  }  
  
}

/** Helper for running HKP protocol commands. */
trait HkpCommand extends PgpCommand {
  def hkpUrl: String
  def hkpClient = hkp.Client(hkpUrl)
}

/** Constructs a new PGP key from user input. */
case class GeneratePgpKey() extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    import ctx.{publicKeyRingFile=>pub,secretKeyRingFile=>sec, log, getPassphrase}
    if(pub.exists) sys.error("Public key ring (" + pub.getAbsolutePath + ") already exists!")
    if(sec.exists) sys.error("Secret key ring (" + sec.getAbsolutePath + ") already exists!")
    if(!pub.getParentFile.exists) IO.createDirectory(pub.getParentFile)
    if(!sec.getParentFile.exists) IO.createDirectory(sec.getParentFile)
    val name = ctx.readInput("Please enter the name associated with the key: ")
    val email = ctx.readInput("Please enter the email associated with the key: ")
    val pw = ctx.readHidden("Please enter the passphrase for the key: ")
    val pw2 = ctx.readHidden("Please re-enter the passphrase for the key: ")
    if(pw != pw2) sys.error("Passphrases do not match!")
    val id = "%s <%s>".format(name, email)
    log.info("Creating a new PGP key, this could take a long time.")
    PGP.makeKeys(id, pw.toCharArray, pub, sec)
    log.info("Public key := " + pub.getAbsolutePath)
    log.info("Secret key := " + sec.getAbsolutePath)
    log.info("Please do not share your secret key.   Your public key is free to share.")
  }
}
object GeneratePgpKey {
  lazy val parser: Parser[GeneratePgpKey] =
    ("gen-key": Parser[String]) map { _ => GeneratePgpKey() }
}

case class SignPgpKey(pubKey: String, privKey: String) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    //TODO - Implement
  }
}

case class SendKey(pubKey: String, hkpUrl: String) extends HkpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    import ctx.{publicKeyRing => pubring, log}
    val key = pubring findPubKey pubKey getOrElse sys.error("Could not find public key: " + pubKey)
    val client = hkpClient
    log.info("Sending " + key + " to " + client)
    client.pushKey(key)
  }
}

object SendKey {
  lazy val parser: Parser[SendKey] = {
    val keyId = token(NotSpace, "key search id/user")
    val hkpUrl = token(NotSpace, "hkp server url")
    ("send-key" ~ Space) ~> keyId ~ (Space ~> hkpUrl) map {
      case key ~ url => SendKey(key, url)
    }
  }
}

case class ReceiveKey(pubKeyId: Long, hkpUrl: String) extends HkpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (hkpClient.getKey(pubKeyId) 
        getOrElse sys.error("Could not find key: " + pubKeyId + " on server " + hkpUrl))
    ctx addPublicKey key
  }
}
object ReceiveKey {
  lazy val parser: Parser[ReceiveKey] = {
    // TODO - More robust...
    val keyId = token(NotSpace, "key id") map (java.lang.Long.parseLong(_, 16))
    val hkpUrl = token(NotSpace, "hkp sever url")
    ("recv-key" ~ Space) ~> keyId ~ (Space ~> hkpUrl) map {
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
  lazy val parser: Parser[ImportKey] = {
    // TODO - implement
    null
  }
}

case class EncryptFile(file: File, pubKey: String) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (ctx.publicKeyRing.findPubKey(pubKey) getOrElse 
        sys.error("Could not find key: " + pubKey))
    //todo - encode
  }
}
object EncryptFile {
  def parser: Parser[EncryptFile] = null
}