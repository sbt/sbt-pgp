package com.jsuereth.pgp

import org.bouncycastle._
import java.io._
import java.util.Date

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._

class IncorrectPassphraseException(msg: String) extends RuntimeException(msg)


/** A SecretKey that can be used to sign things and decrypt messages. */
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
    val privateKey = extractPrivateKey(pass)
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
  def signMessageStream(input: InputStream, name: String, length: Long, output: OutputStream, pass: Array[Char], lastMod: Date = new Date): Unit = {
    val armoredOut = new ArmoredOutputStream(output)
    val pgpPrivKey = extractPrivateKey(pass)
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
  /** Takes a public key and signs it, returning the new public key. */
  // TODO - notation optional?
  def signPublicKey(key: PublicKey, notation: (String,String), pass: Array[Char]): PublicKey = {
    val out = new ArmoredOutputStream(new ByteArrayOutputStream())
    val pgpPrivKey = extractPrivateKey(pass)
    val sGen = new PGPSignatureGenerator(
        nested.getPublicKey().getAlgorithm(), 
        HashAlgorithmTags.SHA1, 
        "BC")
    sGen.initSign(PGPSignature.DIRECT_KEY, pgpPrivKey)
    val bOut = new BCPGOutputStream(out)
    sGen.generateOnePassVersion(false).encode(bOut)
    val spGen = new PGPSignatureSubpacketGenerator()
    val isHumanReadable = true
    spGen.setNotationData(true, isHumanReadable, notation._1, notation._2)
    // TODO - Embedd this key's signatures?
    userIDs.headOption foreach (spGen.setSignerUserID(false, _)) 
    
    val packetVector = spGen.generate()
    sGen.setHashedSubpackets(packetVector)
    bOut.flush()
    out.close()
    // TODO - Is this correct for GnuPG?
    key.userIDs.toSeq match {
      case Seq(user, _*) => PublicKey(PGPPublicKey.addCertification(key, user, sGen.generate()))
      case _             => PublicKey(PGPPublicKey.addCertification(key, sGen.generate()))
    } 
  }
  
  /** Decrypts a file, attempting to write to the filename specified in the message. */
  def decryptFile(file: File, passPhrase: Array[Char]): Unit = {
    decryptHelper(new FileInputStream(file), passPhrase) { msg =>
      val unc = msg.getInputStream
      val outfile = new File(file.getParentFile, msg.getFileName)
      val fOut =  new BufferedOutputStream(new FileOutputStream(outfile))
      val buf = new Array[Byte](1 << 16)
      def read(): Unit = unc.read(buf) match {
        case n if n > 0 => fOut.write(buf, 0, n); read()
        case _          => ()
      }
      read()
      fOut.close()
    }
  }
  
  /** Decrypts a given string message using this secret key. */
  def decryptString(input: String, passPhrase: Array[Char]): String = {
    val bin = new java.io.ByteArrayInputStream(input.getBytes)
    val bout = new java.io.ByteArrayOutputStream
    try decrypt(bin, bout, passPhrase)
    finally {
      bin.close()
      bout.close()
    }
    bout.toString(java.nio.charset.Charset.defaultCharset.name)
  }
  /** Decrypts a given input stream into the output stream.  
   * Note: This ignores fileNames if they are part of the decrypted message.
   */
  def decrypt(input: InputStream, output: OutputStream, passPhrase: Array[Char]): Unit = 
    decryptHelper(input, passPhrase) { msg =>
      val unc = msg.getInputStream
      val fOut =  new BufferedOutputStream(output)
      val buf = new Array[Byte](1 << 16)
      def read(): Unit = unc.read(buf) match {
        case n if n > 0 => fOut.write(buf, 0, n); read()
        case _          => ()
      }
      read()
      fOut.close()
    }
  
  private[this] def decryptHelper[U](input: InputStream, passPhrase: Array[Char])(handler: PGPLiteralData => U): U = {
     val fixIn = PGPUtil.getDecoderStream(input)
    try {
      val objF = new PGPObjectFactory(fixIn)
      // TODO - better method to advance to encrypted data.
      val enc = objF.nextObject match {
        case e: PGPEncryptedDataList => e
        case _                       => objF.nextObject.asInstanceOf[PGPEncryptedDataList] 
      }
      import collection.JavaConverters._
      val it = enc.getEncryptedDataObjects()
      val pbe = (for {
        obj <- it.asInstanceOf[java.util.Iterator[AnyRef]].asScala
        if obj.isInstanceOf[PGPPublicKeyEncryptedData]
      } yield obj.asInstanceOf[PGPPublicKeyEncryptedData]).toTraversable.headOption.getOrElse {
        throw new IllegalArgumentException("Secret key for message not found.")
      }
      // TODO - Better exception?
      if(pbe.getKeyID != this.keyID) throw new KeyNotFoundException(pbe.getKeyID)
      val privKey = extractPrivateKey(passPhrase)
      val clear = pbe.getDataStream(privKey, "BC")
      val plainFact = new PGPObjectFactory(clear)
      // Handle compressed + uncompressed data here.
      def extractLiteral(x: Any): PGPLiteralData = x match {
        case msg: PGPLiteralData => msg
        case cData: PGPCompressedData =>
          // Now we need to read the compressed stream of data.
          val compressedStream = new BufferedInputStream(cData.getDataStream)
          val pgpFact = new PGPObjectFactory(compressedStream)
          extractLiteral(pgpFact.nextObject)
        case msg: PGPOnePassSignature => throw new NotEncryptedMessageException("Message is a signature")
        case _                        => throw new NotEncryptedMessageException("Message is not a simple encyrpted file")      
      }
      val msg = extractLiteral(plainFact.nextObject)
      val result = handler(msg)
      if(pbe.isIntegrityProtected && !pbe.verify()) throw new IntegrityException("Encrypted message failed integrity check.")
      result
    }
  }

  private[this] def extractPrivateKey(passPhrase: Array[Char]) =
    try nested.extractPrivateKey(passPhrase, "BC")
    catch {
      case e: PGPException if e.getMessage.contains("checksum mismatch") => throw new IncorrectPassphraseException("Incorrect passhprase")
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
