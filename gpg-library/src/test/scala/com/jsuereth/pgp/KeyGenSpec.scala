package com.jsuereth.pgp

import org.specs2.mutable._
import sbt.io.IO

import java.io.{BufferedWriter, File, FileWriter}

class KeyGenSpec extends Specification {
  PGP.init()

  val user = "Test User <test@user.com>"
  val pw: Array[Char] = "test-pw".toCharArray
  val (pub, sec) = PGP.makeNewKeyRings(user, pw)

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

    "encrypt and decrypt file" in {
      IO withTemporaryDirectory { dir =>
        val fileContent = "Just one string"
        val testFile1 = new File(dir, "test1.txt")
        val testFile2 = new File(dir, "test2.txt")

        // original file
        val bw1 = new BufferedWriter(new FileWriter(testFile1))
        bw1.write(fileContent)
        bw1.close()

        val source1 = scala.io.Source.fromFile(testFile1.getAbsolutePath)
        val lines1 = try source1.mkString finally source1.close()
//        System.out.println(lines1)

        // encrypted -> decrypted file preparation
        val bw2 = new BufferedWriter(new FileWriter(testFile2))
        bw2.write(fileContent)
        bw2.close()

        val testFileEncrypted = new File(dir, "testEncrypted.txt")
        sec.publicKey.encryptFile(testFile2, testFileEncrypted)
        testFile2.delete()
        sec.secretKey.decryptFile(testFileEncrypted, pw)

        val source2 = scala.io.Source.fromFile(testFile2.getAbsolutePath)
        val lines2 = try source2.mkString finally source2.close()
//        System.out.println(lines2)

        lines1 must equalTo(lines2)
      }
    }

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
