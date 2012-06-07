package com.jsuereth.pgp

import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import java.security.SignatureException

/** Wrapper around a PGP signature for convenience. */
class Signature(val nested: PGPSignature) extends StreamingSaveable {
  /** Returns the name-value string pairs in the notation data occurrences of a signature. */
  // TODO - return a map
  // TODO - Ensure string->string is ok for all returned values...
  object notations extends Traversable[(String,String)] {
    override def foreach[U](f: ((String,String)) => U): Unit = 
      for {
        data <- nested.getHashedSubPackets.getNotationDataOccurences
      } f(data.getNotationName() -> data.getNotationValue())
  }
  def keyID = nested.getKeyID
  def issuerKeyID = nested.getHashedSubPackets.getIssuerKeyID
  def keyExpirationTime = nested.getHashedSubPackets.getKeyExpirationTime
  def signerUserID = Option(nested.getHashedSubPackets.getSignerUserID)
  def signatureType = nested.getSignatureType
  def signatureTypeString = signatureType match {
    case PGPSignature.DIRECT_KEY               => "DirectKey"
    case PGPSignature.BINARY_DOCUMENT          => "Binary Document"
    case PGPSignature.CANONICAL_TEXT_DOCUMENT  => "Canonical Text Doc"
    case PGPSignature.STAND_ALONE              => "Stand-alone"
    case PGPSignature.DEFAULT_CERTIFICATION    => "Default Cert."
    case PGPSignature.NO_CERTIFICATION         => "No Cert."
    case PGPSignature.POSITIVE_CERTIFICATION   => "Positve Cert."
    case PGPSignature.SUBKEY_BINDING           => "Subkey Binding"
    case PGPSignature.PRIMARYKEY_BINDING       => "Primary Key Binding"
    case PGPSignature.KEY_REVOCATION           => "Key Revocation"
    case PGPSignature.SUBKEY_REVOCATION        => "Subkey Revocation"
    case PGPSignature.CERTIFICATION_REVOCATION => "Cert. Revocation"
    case PGPSignature.TIMESTAMP                => "Timestamp"
    case _ => "Not enumerated"
  }
  override def saveTo(output: java.io.OutputStream): Unit = 
    nested encode (new ArmoredOutputStream(output))

  override def toString = 
    "Signature(key=%x,user=%s,notations=%s)" format (
        keyID, 
        signerUserID, 
        notations map { case (k,v) => k + " -> " + v } mkString ",")
}

object Signature {
  def apply(sig: PGPSignature): Signature = new Signature(sig)
  implicit def unwrap(sig: Signature): PGPSignature = sig.nested
}