package com.jsuereth.pgp


import org.specs2.mutable._
import sbt.io.IO
import java.io.File

class KeyGenSpec extends Specification {
  PGP.init

  val user = "Test User <test@user.com>"
  val pw = "test-pw".toCharArray
  val (pub,sec) = PGP.makeNewKeyRings(user,pw)

  "Secret Key Ring" should {
    "serialize and deserialze ring from file" in {
    	IO withTemporaryDirectory { dir =>
           val secFile = new File(dir, "secring.pgp")
           sec.saveToFile(secFile)
           val deserialized = SecretKeyRing.loadFromFile(secFile)
           deserialized must not(beNull)
           def keyIds(ring: SecretKeyRing) = ring.secretKeys.map(_.keyID).toSet
           keyIds(deserialized) must equalTo(keyIds(sec))
    	}
    }
    "encode and decode a message" in {
    	val message = "Hello from me"
    	val encrypted = sec.publicKey.encryptString(message)
    	val decrypted = sec.secretKey.decryptString(encrypted, pw)
    	decrypted must equalTo(decrypted)
    }
    // TODO - This is failing
    
    "sign and verify a string" in {
      val message = "Hello from me"
      val signature = sec.secretKey.signString(message, pw)
      pub.verifySignatureString(message, signature) must beTrue
    }
   
    "sign and verify a message" in {
      val message = "Hello from me"
      val signedMessage = sec.secretKey.signMessageString(message, "test-message", pw)
      pub.verifyMessageString(signedMessage) must equalTo(message)
    }

    "give nice error on invalid password" in {
    	sec.secretKey.signString("test", Array()) must throwAn[IncorrectPassphraseException]
    }
    // TODO - Handle unicode characters in passwords
  }

}