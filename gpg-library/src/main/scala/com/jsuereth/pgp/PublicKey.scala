package com.jsuereth.pgp

import java.io._

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._

/** This class represents a public PGP key. It can be used to encrypt messages for a person and validate that messages were signed correctly. */
class PublicKey(val nested: PGPPublicKey) extends PublicKeyLike with StreamingSaveable {
  /** The identifier for this key. */
  def keyID = nested.getKeyID
  
  def bitStrength = nested.getBitStrength
  def creationTime = nested.getCreationTime
  def fingerprint = nested.getFingerprint
  def isRevoked = nested.isRevoked
  /** Returns the userIDs associated with this public key. */
  object userIDs extends Traversable[String] {
    def foreach[U](f: String => U) = {
      val i = nested.getUserIDs
      while(i.hasNext) f(i.next.toString)
    }
  }
  
  object directKeySignatures extends Traversable[Signature] {
    override def foreach[U](f: Signature => U): Unit = {
      val i = nested.getSignaturesOfType(PGPSignature.DIRECT_KEY)
      while(i.hasNext) {
        f(Signature(i.next.asInstanceOf[PGPSignature]))
      }
    }
  }
  def verifyMessageStream(input: InputStream, output: OutputStream): Boolean =
    verifyMessageStreamHelper(input,output) { id =>
      assert(id == keyID)
      nested
    }
  def verifySignatureStreams(msg: InputStream, signature: InputStream): Boolean = 
    verifySignatureStreamsHelper(msg,signature) { id =>
      if(keyID != id) error("Signature is not for this key.  %x != %x".format(id, keyID))
      nested
    }
  def saveTo(output: OutputStream): Unit = {
    val armoredOut = new ArmoredOutputStream(output)   
    nested.encode(armoredOut)
    armoredOut.close()
  }
  override lazy val toString = "PublicKey(%x, %s)".format(keyID, userIDs.mkString(","))
}
object PublicKey {
  def apply(nested: PGPPublicKey) = new PublicKey(nested)
  implicit def unwrap(key: PublicKey) = key.nested
}
