package com.jsuereth.pgp

import org.bouncycastle._
import java.io._
import java.io.File
import java.math.BigInteger
import java.security.{SecureRandom,Security,KeyPairGenerator,KeyPair}
import java.util.Date

import org.bouncycastle.bcpg._
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ElGamalParameterSpec
import org.bouncycastle.openpgp._

/** A secret PGP key ring. Can be used to decrypt messages and to sign files/messages.  */
class SecretKeyRingCollection(val nested: PGPSecretKeyRingCollection) extends StreamingSaveable {
    /** A collection of all the nested secret key rings. */
  object keyRings extends Traversable[SecretKeyRing] {
    def foreach[U](f: SecretKeyRing => U): Unit = {
      val i = nested.getKeyRings
      while(i.hasNext) 
        f(SecretKeyRing(i.next.asInstanceOf[PGPSecretKeyRing]))
    }
  }
  /** A collection of all the secret keys from all the key rings. */
  def secretKeys: Traversable[SecretKey] = keyRings.view flatMap (_.secretKeys)
  
  /** The default secret key ring to use. */
  def default: SecretKeyRing = keyRings.head
  
  
  /** Finds the first secret key ring that has a public key that:
   *  - A keyID containing the given hex code
   *  - A userID containing the given string
   */
  def findSecretKeyRing(value: String): Option[SecretKeyRing] = 
    (for {
      ring <- keyRings
      key <- ring.secretKeys
      if PGP.isPublicKeyMatching(value)(key.publicKey)
    } yield ring).headOption
    
  /** Finds the first secret key that has:
   *  - A keyID containing the given hex code
   *  - A userID containing the given string
   */
  def findSecretKey(value: String): Option[SecretKey] = {
    secretKeys  find { key =>
      PGP.isPublicKeyMatching(value)(key.publicKey)
    }
  }
  
  def saveTo(output: OutputStream): Unit = {
    val armoredOut = new ArmoredOutputStream(output)   
    nested.encode(armoredOut)
    armoredOut.close()
  }
  
  override def toString = "SecretKeyRingCollection("+secretKeys.mkString(",")+")"
}

object SecretKeyRingCollection extends StreamingLoadable[SecretKeyRingCollection] {
  implicit def unwrap(ring: SecretKeyRingCollection) = ring.nested
  def apply(nested: PGPSecretKeyRingCollection) = new SecretKeyRingCollection(nested)
  def load(input: InputStream) = apply(new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(input)))
}