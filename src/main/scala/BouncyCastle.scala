package com.github.jsuereth.gpg

import sbt._
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


/** This trait represents something that can be saved.   If the class can provide a single saveTo(OutputStream) method, then
 *  this trait provides corresponding saveToFile and saveToString methods.
 */
trait StreamingSaveable {
  /** Saves the current entity to an output stream. */
  def saveTo(output: OutputStream): Unit
  /** Saves the current entity to a file. */
  def saveToFile(file: File): Unit = saveTo(new FileOutputStream(file))
  /** Saves the current entity into a string. */
  def saveToString: String = {
    val baos = new ByteArrayOutputStream
    saveTo(baos)
    baos.toString(java.nio.charset.Charset.defaultCharset.name)
  }
}

/** This trait is for companion objects that have objects which can streamed in.
 */
trait StreamingLoadable[T] {
  /** Loads a {T} from an input stream. */
  def load(input: InputStream): T
  /** Loads a {T} from a file. */
  def loadFromFile(file: File): T = load(new FileInputStream(file))
  /** Loads a {T} from a string. */
  def loadFromString(input: String): T = load(new ByteArrayInputStream(input.getBytes))
}

class SecretKey(val nested: PGPSecretKey) {
  def keyID = nested.getKeyID
  /** @return True if this key can make signatures. */
  def isSigningKey = nested.isSigningKey
  /** @return True if this key is the master of a key ring. */
  def isMasterKey = nested.isMasterKey
  /** Returns the public key associated with this key. */
  def publicKey = PublicKey(nested.getPublicKey)

  /** Creates a signature for the data in the input stream on the output stream.
   * Note: This will close all streams.
   */
  def signStream(in: InputStream, signature: OutputStream, pass: Array[Char]): Unit = {
    val privateKey = nested.extractPrivateKey(pass, "BC")        
    val sGen = new PGPSignatureGenerator(nested.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1, "BC")
    sGen.initSign(PGPSignature.BINARY_DOCUMENT, privateKey)
    val out = new BCPGOutputStream(new ArmoredOutputStream(signature))
    try {
      var ch: Int = in.read()
      while(ch >= 0) {
        sGen.update(ch.asInstanceOf[Byte])
        ch = in.read()
      }
      sGen.generate().encode(out)
    } finally {
      in.close()
      out.close()
    }
  }

  /** Creates a signature for a file and writes it to the signatureFile. */
  def sign(file: File, signatureFile: File, pass: Array[Char]): File = {
    signStream(new FileInputStream(file), new FileOutputStream(signatureFile), pass)
    signatureFile
  }
  /** Creates a signature for the input string. */
  def signString(msg: String, pass: Array[Char]): String = {
    val out = new java.io.ByteArrayOutputStream
    signStream(new java.io.ByteArrayInputStream(msg.getBytes), out, pass)
    out.toString(java.nio.charset.Charset.defaultCharset.name)
  }

  /** Encodes and signs a message into a PGP message. */
  def signMessageStream(input: InputStream, name: String, length: Long, output: OutputStream, pass: Array[Char], lastMod: java.util.Date = new java.util.Date()): Unit = {
    val armoredOut = new ArmoredOutputStream(output)
    val pgpPrivKey = nested.extractPrivateKey(pass, "BC")       
    val sGen = new PGPSignatureGenerator(nested.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1, "BC")
    sGen.initSign(PGPSignature.BINARY_DOCUMENT, pgpPrivKey)
    for(name <- this.publicKey.userIDs) {
      val spGen = new PGPSignatureSubpacketGenerator()
      spGen.setSignerUserID(false, name)
      sGen.setHashedSubpackets(spGen.generate())
    }
    val cGen = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZLIB)
    val bOut = new BCPGOutputStream(cGen open armoredOut)
    sGen.generateOnePassVersion(false).encode(bOut)
    val lGen = new PGPLiteralDataGenerator()
    val lOut = lGen.open(bOut, PGPLiteralData.BINARY, name, length, lastMod)
    val in = new BufferedInputStream(input)
    var ch: Int = in.read()
    while (ch >= 0) {
      lOut.write(ch)
      sGen.update(ch.asInstanceOf[Byte])
      ch = in.read()
    }
    in.close()
    lGen.close()
    sGen.generate().encode(bOut)
    cGen.close()
    armoredOut.close()
  }

  /** Returns a PGP compressed and signed copy of the input string. */
  def signMessageString(input: String, name: String, pass: Array[Char]): String = {
    val out = new java.io.ByteArrayOutputStream
    val bytes = input.getBytes
    signMessageStream(new java.io.ByteArrayInputStream(bytes), name, bytes.length, out, pass)
    out.toString(java.nio.charset.Charset.defaultCharset.name)
  }

  // TODO - Split this into pieces.
  /** Signs an input stream of bytes and writes it to the output stream. */
  def signMessageFile(file: File, out: OutputStream, pass: Array[Char]): Unit = {
    signMessageStream(new java.io.FileInputStream(file), file.getName, file.length, out, pass)
  }

  def userIDs = new Traversable[String] {
    def foreach[U](f: String => U) = {
      val i = nested.getUserIDs
      while(i.hasNext) f(i.next.toString)
    }
  }
  override lazy val toString = "SecretKey(%x, %s)".format(nested.getKeyID, userIDs.mkString(","))
}

object SecretKey {
  def apply(nested: PGPSecretKey) = new SecretKey(nested)
}

/** This trait defines things that can act like a public key.  That is they can verify signed files and messages and encrypt data for an individual. */
trait PublicKeyLike {
  /** Verifies a signed message and extracts the contents.
   * @param input The incoming PGP message.
   * @param output The decoded and verified message.
   */
  def verifyMessageStream(input: InputStream, output: OutputStream): Boolean
  /** Reads in a PGP message from a file, verifies the signature and writes to the output file. */
  final def verifyMessageFile(input: File, output: File): Boolean = {
    val in = new FileInputStream(input)
    val out = new FileOutputStream(output)
    try verifyMessageStream(in,out) finally {
      in.close()
      out.close()
    }
  }
  /** Reads in a PGP message  and from a string, verifies the signature and returns the raw content. */
  final def verifyMessageString(input: String): String = {
    // TODO - better encoding support here.
    val in = new java.io.ByteArrayInputStream(input.getBytes())
    val out = new java.io.ByteArrayOutputStream()
    // TODO - better errors...
    assert(verifyMessageStream(in, out))
    out.toString(java.nio.charset.Charset.defaultCharset.name)
  }
  /** Verifies a signature stream against an input stream.
   * @param msgName the name tied in the signature for this object.  For a file, this is the filename.
   * @param msg  The input stream containing the raw message to verify.
   * @param signature The input stream containing the PGP signature.
   */
  def verifySignatureStreams(msg: InputStream, signature: InputStream): Boolean
  /** Reads in a raw file, verifies the signature file is valid for this file. */
  final def verifySignatureFile(raw: File, signature: File): Boolean = {
    val in = new FileInputStream(raw)
    val in2 = new FileInputStream(signature)
    try verifySignatureStreams(in,in2) finally {
      in.close()
      in2.close()
    }
  }
  /** Reads in a PGP message from a string, verifies the signature string is accurate for the message. */
  final def verifySignatureString(msg: String, signature: String): Boolean = {
    // TODO - better encoding support here.
    val in = new java.io.ByteArrayInputStream(msg.getBytes)
    val in2 = new java.io.ByteArrayInputStream(signature.getBytes)
    // TODO - better errors...
    try verifySignatureStreams(in, in2) finally {
      in.close()
      in2.close()
    }
  }

 protected def verifyMessageStreamHelper(input: InputStream, output: OutputStream)(getKey: Long => PGPPublicKey): Boolean = {
    val in = PGPUtil.getDecoderStream(input)
    val pgpFact = {
      val tmp = new PGPObjectFactory(in)
      val c1 = tmp.nextObject().asInstanceOf[PGPCompressedData]
      new PGPObjectFactory(c1.getDataStream())
    }
    val sigList = pgpFact.nextObject().asInstanceOf[PGPOnePassSignatureList]
    val ops = sigList.get(0)
    val p2 = pgpFact.nextObject().asInstanceOf[PGPLiteralData]
    val dIn = p2.getInputStream()
    val key = getKey(ops.getKeyID)
    ops.initVerify(key, "BC")
    var ch = dIn.read()
    while (ch >= 0) {
      ops.update(ch.asInstanceOf[Byte])
      output.write(ch)
      ch = dIn.read()
    }
    val p3 = pgpFact.nextObject().asInstanceOf[PGPSignatureList]
    ops.verify(p3.get(0))
  }
  protected def verifySignatureStreamsHelper(msg: InputStream, signature: InputStream)(getKey: Long => PGPPublicKey): Boolean = {
    val in = PGPUtil.getDecoderStream(signature)
    // We extract the signature list and object factory based on whether or not the signature is compressed.
    val (sigList,pgpFact) = {
      val pgpFact = new PGPObjectFactory(in)
      val o = pgpFact.nextObject()
      o match {
        case c1: PGPCompressedData => (pgpFact.nextObject.asInstanceOf[PGPSignatureList], new PGPObjectFactory(c1.getDataStream()))
        case sigList: PGPSignatureList   => (sigList, pgpFact)
      }
    }
    val dIn = new BufferedInputStream(msg)
    val sig = sigList.get(0)
    val key = getKey(sig.getKeyID())
    sig.initVerify(key, "BC")
    var ch = dIn.read()
    while(ch >= 0) {
      sig.update(ch.asInstanceOf[Byte])
      ch = dIn.read()
    }
    dIn.close()
    sig.verify()
  }

}

/** This class reprsents a public PGP key. */
class PublicKey(val nested: PGPPublicKey) extends PublicKeyLike with StreamingSaveable {
  /** The identifier for this key. */
  def keyID = nested.getKeyID
  /** Returns the userIDs associated with this public key. */
  def userIDs = new Traversable[String] {
    def foreach[U](f: String => U) = {
      val i = nested.getUserIDs
      while(i.hasNext) f(i.next.toString)
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

/** A wrapper to simplify working with tyhe Java PGP API. */
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
/** A wrapper to simplify working with tyhe Java PGP API. */
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
  def load(input: InputStream) = apply(new PGPSecretKeyRing(PGPUtil.getDecoderStream(input)))
}

object BouncyCastle {
  // TODO - This isn't good enough....  We need to actually deal with classloader issues appropriately.
  // This hack just ensures that our classloader wins when using bouncy castle.  If this library is loaded in many 
  // classloaders, they will be unable to function.   Granted, *one* classloader has to win for bouncy castle anyway.
  // This library will just be documented with the issue so people know to push us down the hierarchy to something
  // early.
  try {
    val newProvider = new BouncyCastleProvider()
    if(java.security.Security.getProvider(newProvider.getName) != null) {
      java.security.Security.removeProvider(newProvider.getName)
    }
    java.security.Security.addProvider(newProvider)
  } catch {
    case t => error("Could not initialize bouncy castle encryption.")
  }
        

  /** This can load your local PGP keyring. */
  def loadPublicKeyRing(file: File) = PublicKeyRing loadFromFile file
  /** This can load your local PGP keyring. */
  def loadSecretKeyRing(file: File) = SecretKeyRing loadFromFile file

  /** Creates a new public/private key pair for PGP encryption using BouncyCastle. */
  def makeKeys(identity: String, passPhrase: Array[Char], publicKey: File, privateKey: File): Unit = {
    val dsaKeyPair = {
       val generator = KeyPairGenerator.getInstance("DSA", "BC")
       generator.initialize(1024)
       generator.generateKeyPair()
    }
    val elgKeyPair = {
      val generator = KeyPairGenerator.getInstance("ELGAMAL", "BC")
      // TODO -For testing, uncommenting these lines drastically speeds the creation of keys.
      //val g = new BigInteger("153d5d6172adb43045b68ae8e1de1070b6137005686d29d3d73a7749199681ee5b212c9b96bfdcfa5b20cd5e3fd2044895d609cf9b410b7a0f12ca1cb9a428cc", 16)
      //val p = new BigInteger("9494fec095f3b85ee286542b3836fc81a5dd0a0349b4c239dd38744d488cf8e31db8bcb7d33b41abb9e5a33cca9144b1cef332c94bf0573bf047a3aca98cdf3b", 16)
      //val elParams = new ElGamalParameterSpec(p, g)
      //generator.initialize(elParams)
      generator.generateKeyPair()
    }
   
    if (!publicKey.getParentFile.exists) { IO.createDirectory(publicKey.getParentFile) }
    if (!privateKey.getParentFile.exists) { IO.createDirectory(privateKey.getParentFile) }
    // TODO - Allow naming the output files.
    exportKeyPair(secretOut = new FileOutputStream(privateKey),
                  publicOut = new FileOutputStream(publicKey),
                  dsa = dsaKeyPair,
                  elg = elgKeyPair,
                  identity = identity,
                  passPhrase = passPhrase)  
 }
 /** Writes a key pair into new key ring files. */
 private def exportKeyPair(
      secretOut: OutputStream,
      publicOut: OutputStream,
      dsa: KeyPair,
      elg: KeyPair,
      identity: String,
      passPhrase: Array[Char]): Unit = {    
    val dsaKeyPair = new PGPKeyPair(PublicKeyAlgorithmTags.DSA, dsa, new Date())
    val elgKeyPair = new PGPKeyPair(PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT, elg, new Date())
    // Define all the settings for our new key ring.
    val keyRingGen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, 
                                             dsaKeyPair,
                                             identity,
                                             SymmetricKeyAlgorithmTags.CAST5, // TODO - Can we use AES_256 for better encryption?
                                             passPhrase,
                                             true,
                                             null,
                                             null,
                                             new SecureRandom(),
                                             "BC")  
    keyRingGen.addSubKey(elgKeyPair)
    // Now generate the public and secret key rings and output them.
    val armoredSecretOut = new ArmoredOutputStream(secretOut)   
    keyRingGen.generateSecretKeyRing().encode(armoredSecretOut)
    armoredSecretOut.close()
    val armoredPublicOut = new ArmoredOutputStream(publicOut)
    keyRingGen.generatePublicKeyRing().encode(armoredPublicOut)
    armoredPublicOut.close()
  }
}
