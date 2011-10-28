package com.jsuereth.pgp

import org.bouncycastle._
import java.io._
import java.math.BigInteger
import java.security.{SecureRandom,Security,KeyPairGenerator,KeyPair}
import java.util.Date

import org.bouncycastle.bcpg._
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ElGamalParameterSpec
import org.bouncycastle.openpgp._

// TODO - make this a real package object?
object PGP {
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

  /** This is a helper method used to make sure the above initialization happens. */
  def init = ()
        

  /** This can load your local PGP keyring. */
  def loadPublicKeyRing(file: File) = PublicKeyRing loadFromFile file
  /** This can load your local PGP keyring. */
  def loadSecretKeyRing(file: File) = SecretKeyRing loadFromFile file

  /** Creates a new public/secret keyring pair in memory. */
  def makeNewKeyRings(identity: String, passPhrase: Array[Char]): (PublicKeyRing, SecretKeyRing) = {
    val gen = makeKeyGeneratorInternal(identity, passPhrase)
    (PublicKeyRing(gen.generatePublicKeyRing()), SecretKeyRing(gen.generateSecretKeyRing()))
  }

  /** Creates a new public/private key pair and saves them in the given files. */
  def makeKeys(identity: String, passPhrase: Array[Char], publicKey: File, secretKey: File): Unit = {
    // TODO - Should we create the parent directory?
    val (pub, sec) = makeNewKeyRings(identity, passPhrase)
    pub saveToFile publicKey
    sec saveToFile secretKey
  }

  /** Helper method for generating new secure PGP keys. */
  protected[pgp] def makeKeyGeneratorInternal(identity: String, passPhrase: Array[Char]): PGPKeyRingGenerator = {
    val dsa = {
       val generator = KeyPairGenerator.getInstance("DSA", "BC")
       generator.initialize(1024)
       generator.generateKeyPair()
    }
    val elg = {
      val generator = KeyPairGenerator.getInstance("ELGAMAL", "BC")
      // TODO -For testing, uncommenting these lines drastically speeds the creation of keys.
      //val g = new BigInteger("153d5d6172adb43045b68ae8e1de1070b6137005686d29d3d73a7749199681ee5b212c9b96bfdcfa5b20cd5e3fd2044895d609cf9b410b7a0f12ca1cb9a428cc", 16)
      //val p = new BigInteger("9494fec095f3b85ee286542b3836fc81a5dd0a0349b4c239dd38744d488cf8e31db8bcb7d33b41abb9e5a33cca9144b1cef332c94bf0573bf047a3aca98cdf3b", 16)
      //val elParams = new ElGamalParameterSpec(p, g)
      //generator.initialize(elParams)
      generator.generateKeyPair()
    }
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
    keyRingGen
  }
}
