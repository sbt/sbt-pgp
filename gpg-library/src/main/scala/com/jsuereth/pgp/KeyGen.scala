package com.jsuereth.pgp

import org.bouncycastle._
import java.math.BigInteger
import java.security.{SecureRandom,Security,KeyPairGenerator,KeyPair}
import java.util.Date

import org.bouncycastle.bcpg._
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ElGamalParameterSpec
import org.bouncycastle.openpgp._

/** Helpers to generate various keys. */
object KeyGen {
  /** Constructs a new RSA PGPKeyPair (for encryption/signing) */
  def makeRsaKeyPair(bitStrength: Int = 2048): PGPKeyPair = {
    val rsa = {
      val generator = KeyPairGenerator.getInstance("RSA", "BC")
      generator.initialize(bitStrength)
      generator.generateKeyPair()
    }
    new PGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, rsa, new Date())    
  }
  /** Constructs a new DSA PGP Pair. (only useful for signing) */
  def makeDsaKeyPair(bitStrength: Int = 2048): PGPKeyPair = {
    val dsa = {
      val generator = KeyPairGenerator.getInstance("DSA", "BC")
      generator.initialize(bitStrength)
      generator.generateKeyPair()
    }
    new PGPKeyPair(PublicKeyAlgorithmTags.DSA, dsa, new Date())
  }
  /** Make a new El Gamal key for signing/encrypting things. */
  def makeElGamalKeyPair(params: Option[ElGamalParameterSpec] = None,
                         encryptOnly: Boolean = false): PGPKeyPair = {
    val elg = {
      val generator = KeyPairGenerator.getInstance("ELGAMAL", "BC")
      params foreach generator.initialize
      generator.generateKeyPair()
    }
    if(encryptOnly) new PGPKeyPair(PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT, elg, new Date())
    else new PGPKeyPair(PublicKeyAlgorithmTags.ELGAMAL_GENERAL, elg, new Date())
  }
  
  /** Makes a new PGP Key ring generator, fully configured with reasonable defaults. */
  def makePgpKeyRingGenerator(
      identity: String, 
      passPhrase: Array[Char], 
      keyPair: PGPKeyPair,
      subKeyPairs: PGPKeyPair*): PGPKeyRingGenerator = {
    val keyRingGen = new PGPKeyRingGenerator(
        PGPSignature.POSITIVE_CERTIFICATION,
        keyPair,
        identity,
        SymmetricKeyAlgorithmTags.CAST5,
        passPhrase,
        true,
        null,
        null,
        new SecureRandom(),
        "BC")
    subKeyPairs foreach keyRingGen.addSubKey
    keyRingGen
  }
  
  /** Instantiates a KeyRingGenerator for combined DSA/ElGamal keys */
  def makeDsaKeyRingGenerator(identity: String, passPhrase: Array[Char]): PGPKeyRingGenerator =
    makePgpKeyRingGenerator(
        identity, 
        passPhrase,
        makeDsaKeyPair(),
        makeElGamalKeyPair(encryptOnly=true))
        
  /** Instantiates a KeyRingGenerator for RSA keys */
  def makeRsaKeyRingGenerator(identity: String, passPhrase: Array[Char]): PGPKeyRingGenerator =
    makePgpKeyRingGenerator(
        identity, 
        passPhrase,
        makeRsaKeyPair())  

  /** Instantiates a KeyRingGenerator for El Gamal keys */
  def makeElGamalKeyRingGenerator(identity: String, passPhrase: Array[Char]): PGPKeyRingGenerator =
    makePgpKeyRingGenerator(
        identity, 
        passPhrase,
        makeElGamalKeyPair())
  
}