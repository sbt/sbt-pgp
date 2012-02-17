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
class SecretKeyRing(val nested: PGPSecretKeyRing) extends StreamingSaveable {

  def extraPublicKeys = new Traversable[PublicKey] {
    def foreach[U](f: PublicKey => U): Unit = {
      val it = nested.getExtraPublicKeys
      while(it.hasNext) f(PublicKey(it.next.asInstanceOf[PGPPublicKey]))
    }
  }

  def secretKeys = new Traversable[SecretKey] {
    def foreach[U](f: SecretKey => U): Unit = {
      val it = nested.getSecretKeys
      while(it.hasNext) f(SecretKey(it.next.asInstanceOf[PGPSecretKey]))
    }
  }
  /** Looks for a secret key with the given id on this key ring. */
  def get(id: Long): Option[SecretKey] = secretKeys find (_.keyID == id)
  /** Gets the secret key with a given id from this key ring or throws. */
  def apply(id: Long): SecretKey = get(id).getOrElse(error("Could not find public key: " + id))
  
  /** The default public key for this key ring. */
  def publicKey = PublicKey(nested.getPublicKey)

  /** Returns the default secret key for this ring. */
  def secretKey = SecretKey(nested.getSecretKey)

  def saveTo(output: OutputStream): Unit = {
    val armoredOut = new ArmoredOutputStream(output)   
    nested.encode(armoredOut)
    armoredOut.close()
  }

  override def toString = "SecretKeyRing(public="+publicKey+",secret="+secretKeys.mkString(",")+")"
}

object SecretKeyRing extends StreamingLoadable[SecretKeyRing] {
  implicit def unwrap(ring: SecretKeyRing) = ring.nested
  def apply(ring: PGPSecretKeyRing) = new SecretKeyRing(ring)
  // TODO - Another way of generating SecretKeyRing from SecretKey objects.
  def load(input: InputStream) = apply(new PGPSecretKeyRing(PGPUtil.getDecoderStream(input)))
  
  /** Creates a new secret key. */
  def create(identity: String, passPhrase: Array[Char]) = 
    apply(KeyGen.makeElGamalKeyRingGenerator(identity, passPhrase).generateSecretKeyRing())
}
