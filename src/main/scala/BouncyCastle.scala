package com.github.jsuereth.gpg

import sbt._
import org.bouncycastle._
import java.io.{FileOutputStream,IOException,OutputStream,File}
import java.security.{InvalidKeyException,KeyPair,KeyPairGenerator,NoSuchProviderException,PrivateKey,PublicKey,SecureRandom,Security,SignatureException}
import java.util.Date

import org.bouncycastle.bcpg.{PublicKeyAlgorithmTags,SymmetricKeyAlgorithmTags}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.{PGPEncryptedData, PGPException, PGPPublicKey, PGPSecretKey, PGPSignature}

object BouncyCastle {

 /** Creates a new public/private key pair for PGP encryption using BouncyCastle. */
 def makeKeys(identity: String, passPhrase: Array[Char], dir: File): Unit = {
   Security addProvider new BouncyCastleProvider()
   val kpg = KeyPairGenerator.getInstance("RSA", "BC")
   kpg.initialize(1024)
   val kp = kpg.generateKeyPair()
   if (!dir.exists) IO.createDirectory(dir)
   // TODO - Allow naming the output files.
   exportKeyPair(secretOut = new FileOutputStream(dir / "secret.asc"),
                 publicOut = new FileOutputStream(dir / "public.asc"),
                 publicKey = kp.getPublic(),
                 privateKey = kp.getPrivate(),
                 identity = identity,
                 passPhrase = passPhrase)  
 }

 private def exportKeyPair(
      secretOut: OutputStream,
      publicOut: OutputStream,
      publicKey: PublicKey,
      privateKey: PrivateKey,
      identity: String,
      passPhrase: Array[Char]): Unit = {    
    // Create a new secret key.
    val secretKey = new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION, 
                                     PublicKeyAlgorithmTags.RSA_GENERAL, 
                                     publicKey,
                                     privateKey,
                                     new Date(), 
                                     identity,
                                     SymmetricKeyAlgorithmTags.CAST5,
                                     passPhrase,
                                     null,
                                     null,
                                     new SecureRandom(),
                                     "BC");
        
    secretKey.encode(secretOut)
    secretOut.close()
    val key = secretKey.getPublicKey();
    key.encode(publicOut)        
    publicOut.close()
  }
}
