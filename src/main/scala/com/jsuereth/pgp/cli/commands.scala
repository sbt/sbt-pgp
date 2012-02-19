package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._

/** Represents a PgpCommand */
sealed trait PgpCommand {
  def run(ctx: PgpCommandContext): Unit
}
object PgpCommand {
  def parser(ctx: PgpStaticContext): Parser[PgpCommand] =
    (GeneratePgpKey.parser(ctx) |
     ListKeys.parser(ctx) |
     SendKey.parser(ctx) |
     ReceiveKey.parser(ctx) |
     ImportKey.parser(ctx) |
     EncryptMessage.parser(ctx) |
     SignPublicKey.parser(ctx) |
     ExportPublicKey.parser(ctx))
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
  def parser(ctx: PgpStaticContext): Parser[GeneratePgpKey] =
    token(("gen-key": Parser[String]) map { _ => GeneratePgpKey() })
}

case class SignPublicKey(pubKey: String, notation: (String,String)) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val matches = for {
      ring <- ctx.publicKeyRing.keyRings
      key <- ring.publicKeys
      if PGP.isPublicKeyMatching(pubKey)(key)
    } yield ring -> key
    
    val newpubringcol = matches match {
      case Seq((ring, key)) =>
        val newkey = ctx.secretKeyRing.secretKey.signPublicKey(key, notation, ctx.getPassphrase)
        val newpubring = ring :+ newkey
        (ctx.publicKeyRing removeRing ring)  :+ newpubring
      case Seq()            => sys.error("Could not find key: " + pubKey)
      case matches          => sys.error("Found more than on pulic key: " + matches.map(_._2).mkString(","))
    }
    newpubringcol saveToFile ctx.publicKeyRingFile
  }
}
object SignPublicKey {
  def parser(ctx: PgpStaticContext): Parser[SignPublicKey] =
    ((token("sign-key") ~ Space) ~> existingKeyIdOrUserOption(ctx) ~ (Space ~> attribute)) map {
      case key ~ attr => SignPublicKey(key, attr)
    }
}

case class SendKey(pubKey: String, hkpUrl: String) extends HkpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    import ctx.{publicKeyRing => pubring, log}
    val key = pubring findPubKey pubKey getOrElse sys.error("Could not find public key: " + pubKey)
    val client = hkpClient
    log.info("Sending " + key + " to " + client)
    client.pushKey(key, { s: String => log.debug(s) })
  }
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
    ctx addPublicKey key
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
    val key = (ctx.publicKeyRing.findPubKey(pubKey) getOrElse 
        sys.error("Could not find key: " + pubKey))
    key.encryptFile(file, new File(file.getAbsolutePath + ".asc"))
  }
}
object EncryptFile {
  def parser(ctx: PgpStaticContext): Parser[EncryptFile] = null
}

case class EncryptMessage(msg: String, pubKey: String) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (ctx.publicKeyRing.findEncryptionKey(pubKey) getOrElse 
        sys.error("Could not find key: " + pubKey))
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

case class ExportPublicKey(id: String) extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    val key = (ctx.publicKeyRing.findPubKey(id) getOrElse
        sys.error("Could not find key: " + id))
    ctx.output(key.saveToString)
  }
}
object ExportPublicKey {
  def parser(ctx: PgpStaticContext): Parser[ExportPublicKey] = {
    (token("export-pub-key") ~ Space) ~> existingKeyIdOrUserOption(ctx) map ExportPublicKey.apply
  }
}
case class ListKeys() extends PgpCommand {
  def run(ctx: PgpCommandContext): Unit = {
    def printKey(k: PublicKey) = { 
      val hexkey: String = ("%x" format (k.keyID)).takeRight(8)
      val strength = k.algorithmName + "@" + k.bitStrength.toString
      val head = if(k.isMasterKey()) "pub" else "sub"
      val date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(k.getCreationTime)
      val userStrings = k.userIDs.map("uid\t                \t" +).mkString("","\n","\n")
      head +"\t"+ strength +"/" + hexkey +"\t"+ date + "\n" + userStrings
    }
    def printRing(r: PublicKeyRing) = 
      r.publicKeys map printKey mkString "\n"
    ctx output {
      val path = ctx.publicKeyRingFile.getAbsolutePath
      val line = Stream.continually('-').take(path.length).mkString("")
      path + "\n" + line + "\n" + (ctx.publicKeyRing.keyRings map printRing mkString "\n")  
    }
  }
}
object ListKeys {
  def parser(ctx: PgpStaticContext): Parser[ListKeys] =
    token("list-keys") map { _ => ListKeys() }
}