package com.jsuereth.pgp
package cli

import sbt._
import sbt.complete._
import sbt.complete.DefaultParsers._
import CommonParsers._
import org.bouncycastle.openpgp.PGPPublicKeyRing


/** A context for accepting user input. */
trait UICommandContext {
  /** Displays the message to the user and accepts their input. */
  def readInput(msg: String): String
  /** Displays the message to the user and accepts their input. 
   * Replaces characters entered with '*'.
   */
  def readHidden(msg: String): String
  /** Prints the given text to whatever output we're using. */
  def output[A](msg: => A): Unit
  /** Logs information */  
  def log: Logger
}

/** Context usable by command parsers. */
trait PgpStaticContext {
  def publicKeyRingFile: File
  def secretKeyRingFile: File
  // Derived methods
  def publicKeyRing: PublicKeyRingCollection = PGP loadPublicKeyRingCollection publicKeyRingFile
  def secretKeyRing: SecretKeyRing = PGP loadSecretKeyRing secretKeyRingFile
}
  
trait DelegatingPgpStaticContext extends PgpStaticContext{
  def ctx: PgpStaticContext
  override def publicKeyRing = ctx.publicKeyRing
  override def publicKeyRingFile = ctx.publicKeyRingFile
  override def secretKeyRing = ctx.secretKeyRing
  override def secretKeyRingFile = ctx.secretKeyRingFile
}

/** The context used when running PGP commands. */
trait PgpCommandContext extends PgpStaticContext with UICommandContext {
  /** Prompts user to input a passphrase. */
  def inputPassphrase: Array[Char]
  /** Perform an action with a passphrase.  This will ensure caching or other magikz */
  def withPassphrase[U](f: Array[Char] => U): U
  def addPublicKeyRing(key: PublicKeyRing): Unit = {
    key.masterKey match {
      case Some(mk) if publicKeyRing.publicKeys.map(_.keyID).toSet.apply(mk.keyID) =>
        val badring = for {
          ring <- publicKeyRing.keyRings
          pk <- ring.publicKeys
          if pk.keyID == mk.keyID
        } yield ring
        val newring = badring.foldLeft(publicKeyRing) {
          (col, ring) =>
            col removeRing ring
        }
        val newring2 = newring :+ key
        newring2 saveToFile publicKeyRingFile
      case _ =>
        val newring = publicKeyRing :+ key
        newring saveToFile publicKeyRingFile
    }
  } 
  def addPublicKey(key: PublicKey): Unit =
    addPublicKeyRing(PublicKeyRing from key)
}