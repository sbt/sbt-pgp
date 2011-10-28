package com.jsuereth.pgp


import java.io._
import java.util.Date

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._


/** A collection of public keys, known as a 'ring'. */
class PublicKeyRing(val nested: PGPPublicKeyRing) extends PublicKeyLike with StreamingSaveable{
  /** Adds a key to this key ring and returns the new key ring. */
  def +:(key: PGPPublicKey): PublicKeyRing = 
    PublicKeyRing(PGPPublicKeyRing.insertPublicKey(nested, key))
  /** Adds a key to this key ring and returns the new key ring. */
  def :+(key: PGPPublicKey): PublicKeyRing = key +: this
  /** Removes a key from this key ring and returns the new key ring. */
  def removeKey(key: PGPPublicKey): PublicKeyRing =
    PublicKeyRing(PGPPublicKeyRing.removePublicKey(nested, key))
  /** A collection that will traverse all public keys in this key ring. */
  def publicKeys = new Traversable[PublicKey] {
    def foreach[U](f: PublicKey => U): Unit = {
      val it = nested.getPublicKeys
      while(it.hasNext) {
        f(PublicKey(it.next.asInstanceOf[PGPPublicKey]))
      }
    }
  }
  /** A collection that will traverse all keys that can be used to encrypt data. */
  def encryptionKeys = publicKeys.view filter (_.isEncryptionKey)
  /** Returns the default key used to encrypt messages. */
  def defaultEncryptionKey = encryptionKeys.headOption getOrElse error("No encryption key found.")
  def verifyMessageStream(input: InputStream, output: OutputStream): Boolean =
    verifyMessageStreamHelper(input,output)(nested.getPublicKey)
  def verifySignatureStreams(msg: InputStream, signature: InputStream): Boolean = 
    verifySignatureStreamsHelper(msg,signature)(nested.getPublicKey)
  def saveTo(output: OutputStream): Unit = {
    val armoredOut = new ArmoredOutputStream(output)   
    nested.encode(armoredOut)
    armoredOut.close()
  }
  override def toString = "PublicKeyRing("+publicKeys.mkString(",")+")"
}
object PublicKeyRing extends StreamingLoadable[PublicKeyRing] {
  implicit def unwrap(ring: PublicKeyRing): PGPPublicKeyRing = ring.nested
  def apply(nested: PGPPublicKeyRing) = new PublicKeyRing(nested)
  def load(input: InputStream) = apply(new PGPPublicKeyRing(PGPUtil.getDecoderStream(input)))
}
