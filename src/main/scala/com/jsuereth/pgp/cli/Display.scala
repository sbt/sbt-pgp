package com.jsuereth.pgp
package cli

/** Helper for printing key info to console. */
object Display {
  
  def printFileHeader(f: java.io.File) = {
    val path = f.getAbsolutePath
    val line = Stream.continually('-').take(path.length).mkString("")
    path + "\n" + line + "\n"
  }
  
  def printKey(k: PublicKey) = { 
    val hexkey: String = ("%x" format (k.keyID)).takeRight(8)
    val strength = k.algorithmName + "@" + k.bitStrength.toString
    val head = if(k.isMasterKey()) "pub" else "sub"
    val date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(k.getCreationTime)
    val userStrings =
      if(k.userIDs.isEmpty) ""
      else k.userIDs.map("uid\t                \t" +).mkString("","\n","\n")
    head +"\t"+ strength +"/" + hexkey +"\t"+ date + "\n" + userStrings
  }
  
  def printSignature(s: Signature) = {
    val hexKey: String = ("%x" format (s.keyID)).takeRight(8)
    val notationsString = 
      if(s.notations.isEmpty) ""
      else s.notations.map { case (l,r) => "note\t\t\t\t" +l + "=" + r}.mkString("\n", "\n", "")
    val header = "sig"
    val user = s.signerUserID getOrElse ""
    "%s\t%s@%s\t%s%s" format(header, hexKey, s.signatureTypeString, user, notationsString)
  }
  
  def printSignatures(k: PublicKey) = 
    k.signatures map printSignature mkString "\n"
  def printKeyWithSignatures(r: PublicKey) =
    printKey(r) + printSignatures(r) + "\n"
  def printRingWithSignatures(r: PublicKeyRing) =
    r.publicKeys map printKeyWithSignatures mkString "\n"
  def printRing(r: PublicKeyRing) = 
    r.publicKeys map printKey mkString "\n"
}