package com.jsuereth.pgp


import java.io._
import java.util.Date

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._


/** A collection of public keys, known as a 'ring'. */
class PublicKeyRing(val nested: PGPPublicKeyRing) extends PublicKeyLike with StreamingSaveable{
  /** Adds a key to this key ring and returns the new key ring. */
  def +:(key: PublicKey): PublicKeyRing = 
    PublicKeyRing(PGPPublicKeyRing.insertPublicKey(nested, key.nested))
  /** Adds a key to this key ring and returns the new key ring. */
  def :+(key: PublicKey): PublicKeyRing = key +: this
  /** Removes a key from this key ring and returns the new key ring. */
  def removeKey(key: PGPPublicKey): PublicKeyRing =
    PublicKeyRing(PGPPublicKeyRing.removePublicKey(nested, key))
  
  /** Looks for a public key with the given id on this key ring. */
  def get(id: Long): Option[PublicKey] = publicKeys find (_.keyID == id)
  /** Gets the public key with a given id from this key ring or throws. */
  def apply(id: Long): PublicKey = get(id).getOrElse(error("Could not find public key: " + id))
  /** A collection that will traverse all public keys in this key ring. */
  def publicKeys = new Traversable[PublicKey] {
    def foreach[U](f: PublicKey => U): Unit = {
      val it = nested.getPublicKeys
      while(it.hasNext) {
        f(PublicKey(it.next.asInstanceOf[PGPPublicKey]))
      }
    }
  }
  def masterKey = publicKeys find (_.isMasterKey)
  
  /** Finds the first public key that has:
   *  - A keyID containing the given hex code
   *  - A userID containing the given string
   */
  def findPubKey(value: String): Option[PublicKey] = {
    def hasKeyId(k: PublicKey) = k.keyID.toHexString contains value
    def hasUserId(k: PublicKey) = k.userIDs.exists(_ contains value)
    def isValidPubKey(k: PublicKey) = hasKeyId(k) || hasUserId(k)
    publicKeys find isValidPubKey
  }
  /** A collection that will traverse all keys that can be used to encrypt data. */
  def encryptionKeys = publicKeys.view filter (_.isEncryptionKey)
  /** Finds the first encryption key that has:
   *  - A keyID containing the given hex code
   *  - A userID containing the given string
   */
  def findEncryptionKey(value: String): Option[PublicKey] = {
    def hasKeyId(k: PublicKey) = k.keyID.toHexString contains value
    def hasUserId(k: PublicKey) = k.userIDs.exists(_ contains value)
    def isValidPubKey(k: PublicKey) = hasKeyId(k) || hasUserId(k)
    encryptionKeys find isValidPubKey
  }
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
  def from(key: PublicKey): PublicKeyRing = loadFromString(key.saveToString)
}
