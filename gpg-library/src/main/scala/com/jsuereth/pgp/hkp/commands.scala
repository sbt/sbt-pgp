package com.jsuereth.pgp
package hkp


/** A HKP command that can be sent to a HKP server. */
trait HkpCommand {
  /** The URL to issue the command to. */
  def url: String
  /** The GET/POST arguments for the HTTP command. */
  def vars: Map[String,String] = Map("options" -> "mr")
  
}

/** Any of the lookup commands. */
trait Lookup extends HkpCommand {
  override def url = "/pks/lookup"
}

/** Requests a key from the server. */
case class GetKey(id: Long) extends Lookup{
   override def vars: Map[String,String] = 
     Map("op" -> "get",
         "search" -> ("0x"+idToString(id))) ++ super.vars
}

case class Find(search: String) extends Lookup {
 override def vars: Map[String,String] = 
     Map("op" -> "index",
         "search" -> search) ++ super.vars
}

case class AddKey(key: StreamingSaveable) extends Lookup {
  override def url = "/pks/add"
  override def vars: Map[String,String] = 
     Map("keytext" -> key.saveToString) ++ super.vars
}