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
  /** Loads a collection of public key rings from a file. */
  def loadPublicKeyRingCollection(file: File) = PublicKeyRingCollection loadFromFile file
  /** Loads a collection of public key rings from a file. */
  def loadSecretKeyRingCollection(file: File) = SecretKeyRingCollection loadFromFile file
  
  /** Creates a new public/secret keyring pair in memory. */
  def makeNewKeyRings(identity: String, passPhrase: Array[Char]): (PublicKeyRing, SecretKeyRing) = {
    val gen = KeyGen.makeRsaKeyRingGenerator(identity, passPhrase)
    (PublicKeyRing(gen.generatePublicKeyRing()), SecretKeyRing(gen.generateSecretKeyRing()))
  }

  /** Creates a new public/private key pair and saves them in the given files. */
  def makeKeys(identity: String, passPhrase: Array[Char], publicKey: File, secretKey: File): Unit = {
    // TODO - Should we create the parent directory?
    val (pub, sec) = makeNewKeyRings(identity, passPhrase)
    // TODO - GPG usually saves out key-rings, but I think the file formats are basically the same.
    pub saveToFile publicKey
    sec saveToFile secretKey
  }
  
  def isPublicKeyMatching(value: String)(k: PublicKey) = {
    val hasKeyId = k.keyID.toHexString contains value
    val hasUserId = k.userIDs.exists(_ contains value)
    hasKeyId || hasUserId
  }
}
