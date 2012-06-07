package com.jsuereth.pgp

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import java.io.{InputStream,OutputStream}

/** A collection of nested key rings. */
class PublicKeyRingCollection(val nested: PGPPublicKeyRingCollection) extends PublicKeyLike with StreamingSaveable {
  /** A collection of all the nested key rings. */
  object keyRings extends Traversable[PublicKeyRing] {
    def foreach[U](f: PublicKeyRing => U): Unit = {
      val i = nested.getKeyRings
      while(i.hasNext) 
        f(PublicKeyRing(i.next.asInstanceOf[PGPPublicKeyRing]))
    }
  }
  /** A collection of all the public keys from all the key rings. */
  def publicKeys: Traversable[PublicKey] = keyRings.view flatMap (_.publicKeys)
  
  /** Finds the first public key ring that has a public key that:
   *  - A keyID containing the given hex code
   *  - A userID containing the given string
   */
  def findPubKeyRing(value: String): Option[PublicKeyRing] = 
    (for {
      ring <- keyRings
      pubKey <- ring.publicKeys
      if PGP.isPublicKeyMatching(value)(pubKey)
    } yield ring).headOption
  /** Finds the first public key that has:
   *  - A keyID containing the given hex code
   *  - A userID containing the given string
   */
  def findPubKey(value: String): Option[PublicKey] = {
    publicKeys find PGP.isPublicKeyMatching(value)
  }
  /** Finds a public key using an exact id. */
  def getKey(id: Long): Option[PublicKey] =
    publicKeys find (_.keyID == id)
  /** A collection that will traverse all keys that can be used to encrypt data. */
  def encryptionKeys = publicKeys filter (_.isEncryptionKey)
  /** Finds the first encryption key that has:
   *  - A keyID containing the given hex code
   *  - A userID containing the given string
   */
  def findEncryptionKey(value: String): Option[PublicKey] = {
    encryptionKeys find PGP.isPublicKeyMatching(value)
  }
  
  def +:(ring: PublicKeyRing): PublicKeyRingCollection =
    PublicKeyRingCollection(PGPPublicKeyRingCollection.addPublicKeyRing(nested, ring.nested))
  def :+(ring: PublicKeyRing): PublicKeyRingCollection = ring +: this  
  def removeRing(ring: PublicKeyRing): PublicKeyRingCollection =
    PublicKeyRingCollection(PGPPublicKeyRingCollection.removePublicKeyRing(nested, ring.nested))
  
  private[this] def pkeyLookup(id: Long): PGPPublicKey =
    getKey(id) map (_.nested) getOrElse (throw new KeyNotFoundException(id))
  def verifyMessageStream(input: InputStream, output: OutputStream): Boolean =
    verifyMessageStreamHelper(input,output)(pkeyLookup)
  def verifySignatureStreams(msg: InputStream, signature: InputStream): Boolean = 
    verifySignatureStreamsHelper(msg,signature)(pkeyLookup)
  def saveTo(output: OutputStream): Unit = {
    val armoredOut = new ArmoredOutputStream(output)   
    nested.encode(armoredOut)
    armoredOut.close()
  }
  override def toString = "PublicKeyRingCollecton(\n\t%s\n)" format(keyRings mkString ",\n\t")
}
object PublicKeyRingCollection extends StreamingLoadable[PublicKeyRingCollection] {
  implicit def unwrap(ring: PublicKeyRingCollection) = ring.nested
  def apply(nested: PGPPublicKeyRingCollection) = new PublicKeyRingCollection(nested)
  def load(input: InputStream) = apply(new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(input)))
}
