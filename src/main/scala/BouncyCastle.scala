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


class SecretKey(val nested: PGPSecretKey) {

   def isSigningKey = nested.isSigningKey
   def isMasterKey = nested.isMasterKey
   /** Returns the public key associated with this key. */
   def publicKey = PublicKey(nested.getPublicKey)
   /** Creates a signature for a file and writes it to the signatureFile. */
   def sign(file: File, signatureFile: File, pass: Array[Char]): File = {
     val privateKey = nested.extractPrivateKey(pass, "BC")        
     val sGen = new PGPSignatureGenerator(nested.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1, "BC")
     sGen.initSign(PGPSignature.BINARY_DOCUMENT, privateKey)
     val in = new FileInputStream(file)
     val out = new BCPGOutputStream(new ArmoredOutputStream(new FileOutputStream(signatureFile)))
     try {
       var ch: Int = in.read()
       while(ch >= 0) {
         sGen.update(ch.asInstanceOf[Byte])
         ch = in.read()
       }
       sGen.generate().encode(out);
     } finally {
       in.close()
       out.close()
     }
     signatureFile
   }
   /** Signs an input stream of bytes and writes it to the output stream. */
   def encryptAndSignFile(file: File, out: OutputStream, pass: Array[Char]): Unit = {
    // TODO - get secret key
    val pgpPrivKey = nested.extractPrivateKey(pass, "BC");        
    val sGen = new PGPSignatureGenerator(nested.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1, "BC")
    sGen.initSign(PGPSignature.BINARY_DOCUMENT, pgpPrivKey)
    for(name <- this.publicKey.userIDs) {
      val spGen = new PGPSignatureSubpacketGenerator()
      spGen.setSignerUserID(false, name)
      sGen.setHashedSubpackets(spGen.generate())
    }
    val cGen = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZLIB)
    val bOut = new BCPGOutputStream(cGen open out)
    sGen.generateOnePassVersion(false).encode(bOut)
    val lGen = new PGPLiteralDataGenerator()
    val lOut = lGen.open(bOut, PGPLiteralData.BINARY, file)
    val in = new BufferedInputStream(new FileInputStream(file))
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

class PublicKey(val nested: PGPPublicKey) {
  /** Returns true if a signature is valid for this key. */
  def verifySignature(input: InputStream): Boolean = {
    val in = PGPUtil.getDecoderStream(input)
    val pgpFact = {
      val tmp = new PGPObjectFactory(in)
      val c1 = tmp.nextObject().asInstanceOf[PGPCompressedData]
      new PGPObjectFactory(c1.getDataStream())
    }
    val p1 = pgpFact.nextObject().asInstanceOf[PGPOnePassSignatureList]
    val ops = p1.get(0);
    val p2 = pgpFact.nextObject().asInstanceOf[PGPLiteralData]
    val dIn = p2.getInputStream()
    assert(ops.getKeyID() == nested.getKeyID)
    ops.initVerify(nested, "BC");
    var ch = dIn.read()
    while (ch >= 0) {
      ops.update(ch.asInstanceOf[Byte])
      ch = dIn.read()
    }
    val p3 = pgpFact.nextObject().asInstanceOf[PGPSignatureList]
    ops.verify(p3.get(0))
  }
  def userIDs = new Traversable[String] {
    def foreach[U](f: String => U) = {
      val i = nested.getUserIDs
      while(i.hasNext) f(i.next.toString)
    }
  }
  override lazy val toString = "PublicKey(%x, %s)".format(nested.getKeyID, userIDs.mkString(","))
}
object PublicKey {
  def apply(nested: PGPPublicKey) = new PublicKey(nested)
  implicit def unwrap(key: PublicKey) = key.nested
}

/** A wrapper to simplify working with tyhe Java PGP API. */
class PublicKeyRing(val nested: PGPPublicKeyRing) {
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
  def defaultEncryptionKey = encryptionKeys.headOption getOrElse error("No encryption key found.")

  /** Returns true if a signature is valid. */
  def extractAndVerify(input: InputStream, dir: File): Boolean = {
    val in = PGPUtil.getDecoderStream(input)
    val pgpFact = {
      val tmp = new PGPObjectFactory(in)
      val c1 = tmp.nextObject().asInstanceOf[PGPCompressedData]
      new PGPObjectFactory(c1.getDataStream())
    }
    val p1 = pgpFact.nextObject().asInstanceOf[PGPOnePassSignatureList]
    val ops = p1.get(0);
    val p2 = pgpFact.nextObject().asInstanceOf[PGPLiteralData]
    val dIn = p2.getInputStream()
    val key = nested.getPublicKey(ops.getKeyID())
    // TODO - Optionally write the file...
    val out = new FileOutputStream(new File(dir, p2.getFileName()));
    ops.initVerify(key, "BC");
    var ch = dIn.read()
    while (ch >= 0) {
      ops.update(ch.asInstanceOf[Byte])
      out.write(ch)
      ch = dIn.read()
    }
    out.close()
    val p3 = pgpFact.nextObject().asInstanceOf[PGPSignatureList]
    ops.verify(p3.get(0))
  }
  /** Saves this key ring to a stream. */
  def saveTo(output: OutputStream) = nested.encode(output)
  override def toString = "PublicKeyRing("+publicKeys.mkString(",")+")"
}
object PublicKeyRing {
  implicit def unwrap(ring: PublicKeyRing): PGPPublicKeyRing = ring.nested
  def apply(nested: PGPPublicKeyRing) = new PublicKeyRing(nested)
  def load(input: InputStream) = apply(new PGPPublicKeyRing(PGPUtil.getDecoderStream(input)))
  def loadFromFile(file: File) = load(new FileInputStream(file))
}
/** A wrapper to simplify working with tyhe Java PGP API. */
class SecretKeyRing(val nested: PGPSecretKeyRing) {

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

  override def toString = "SecretKeyRing(public="+publicKey+",secret="+secretKeys.mkString(",")+")"
}
object SecretKeyRing {
  implicit def unwrap(ring: SecretKeyRing) = ring.nested
  def apply(ring: PGPSecretKeyRing) = new SecretKeyRing(ring)
  def load(input: InputStream) = apply(new PGPSecretKeyRing(PGPUtil.getDecoderStream(input)))
  def loadFromFile(file: File) = load(new FileInputStream(file))
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
       val generator = KeyPairGenerator.getInstance("DSA", "BC");
       generator.initialize(1024);
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
    val dsaKeyPair = new PGPKeyPair(PublicKeyAlgorithmTags.DSA, dsa, new Date());
    val elgKeyPair = new PGPKeyPair(PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT, elg, new Date());
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
