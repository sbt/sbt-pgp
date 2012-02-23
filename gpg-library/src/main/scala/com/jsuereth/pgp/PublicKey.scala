package com.jsuereth.pgp

import java.io._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import java.security.SecureRandom

/** This class represents a public PGP key. It can be used to encrypt messages for a person and validate that messages were signed correctly. */
class PublicKey(val nested: PGPPublicKey) extends PublicKeyLike with StreamingSaveable {
  /** The identifier for this key. */
  def keyID = nested.getKeyID
  
  def bitStrength = nested.getBitStrength
  def creationTime = nested.getCreationTime
  def fingerprint = nested.getFingerprint
  def isRevoked = nested.isRevoked
  def algorithm = nested.getAlgorithm
  def algorithmName = nested.getAlgorithm match {
    case PublicKeyAlgorithmTags.RSA_ENCRYPT |
         PublicKeyAlgorithmTags.RSA_GENERAL |
         PublicKeyAlgorithmTags.RSA_SIGN        => "RSA"
    case PublicKeyAlgorithmTags.DSA             => "DSA"
    case PublicKeyAlgorithmTags.EC              => "EC"
    case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT |       
         PublicKeyAlgorithmTags.ELGAMAL_GENERAL => "ElGamal"
    case PublicKeyAlgorithmTags.ECDSA           => "ECDSA"
    case _ => "Unknown"
  }
  /** Returns the userIDs associated with this public key. */
  object userIDs extends Traversable[String] {
    def foreach[U](f: String => U) = {
      val i = nested.getUserIDs
      while(i.hasNext) f(i.next.toString)
    }
  }
  
  object signatures extends Traversable[Signature] {
    override def foreach[U](f: Signature => U): Unit = {
      val i = nested.getSignatures()
      while(i.hasNext) {
        f(Signature(i.next.asInstanceOf[PGPSignature]))
      }
    }
  }
  
  def signaturesForId(id: String): Traversable[Signature] = new Traversable[Signature] {
    override def foreach[U](f: Signature => U): Unit = {
      val i = nested.getSignaturesForID(id)
      while(i.hasNext) {
        f(Signature(i.next.asInstanceOf[PGPSignature]))
      }
    }
  }
  
  def directKeySignatures: Traversable[Signature] =
    signatures.view filter (_.signatureType == PGPSignature.DIRECT_KEY)
  
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
  /** Encrypts a file such that only the secret key associated with this public key can decrypt. */
  def encryptFile(input: File, output: File): Unit = {
    val in = new FileInputStream(input)
    val out = new FileOutputStream(output)
    try encrypt(in, out, input.getName, input.length, new java.util.Date(input.lastModified))
    finally {
      in.close()
      out.close()
    }
  }
  /** Encrypts a string such that only the secret key associated with this public key could decrypt. */
  def encryptString(input: String): String = {
    val bytes = input.getBytes
    val in = new java.io.ByteArrayInputStream(bytes)
    val out = new java.io.ByteArrayOutputStream
    // TODO - better errors...
    try encrypt(in, out, "", bytes.length, new java.util.Date()) finally {
      in.close()
      out.close()
    }
    out.toString(java.nio.charset.Charset.defaultCharset.name)
  }
  
  def encrypt(
      data: InputStream, 
      output: OutputStream, 
      fileName: String, 
      size: Long, 
      lastMod: java.util.Date = new java.util.Date): Unit = {
    val aout = new ArmoredOutputStream(output)
    // TODO - Compress on the way into bytes
    val bytes = Array.empty[Byte]
    val encGen = new PGPEncryptedDataGenerator(
                SymmetricKeyAlgorithmTags.CAST5, 
                true, 
                new SecureRandom(), 
                "BC")
    encGen.addMethod(nested)
    val cOut = encGen.open(aout, new Array[Byte](1024))
    val lit = new PGPLiteralDataGenerator
    val lOut = lit.open(cOut, PGPLiteralDataGenerator.BINARY, fileName, size, lastMod)
    val buffer = new Array[Byte](1024)
    def read(): Unit = data.read(buffer) match {
      case n if n > 0 => lOut.write(buffer, 0, n); read()
      case _          => ()
    }
    read()
    lit.close()
    cOut.close()
    aout.close()
    data.close() 
  }
  
  def saveTo(output: OutputStream): Unit = {
    val armoredOut = new ArmoredOutputStream(output)   
    nested.encode(armoredOut)
    armoredOut.close()
  }
  override lazy val toString = "PublicKey(%x, %s, %s@%d)".format(keyID, userIDs.mkString(","), algorithmName, bitStrength)
}
object PublicKey {
  def apply(nested: PGPPublicKey) = new PublicKey(nested)
  implicit def unwrap(key: PublicKey) = key.nested
}
