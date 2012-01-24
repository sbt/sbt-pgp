package com.jsuereth.pgp
package hkp

/** Represents a client connected to a PGP public key server. */
trait Client {
  /** Retreives a PGP Public Key from the server. */
  def getKey(id: Long): Option[PublicKey]
  /** Pushes a public key to a key server. */
  def pushKey(key: PublicKey): Unit
  //def search(term: String):  Seq[KeyIndexResult]
}

// case class KeyIndexResult(id: String, identity: String, date: java.util.Date)

private[hkp] class DispatchClient(serverUrl: String) extends Client {
  
  import dispatch._
  import Http._
  import util.control.Exception.catching
  
  /** Attempts to pull a public key from the HKP server.
   * @return Some(key) if successful, None otherwise.
   */
  def getKey(id: Long): Option[PublicKey] =
    for {
      ring <- catching(classOf[Exception]) opt Http(
          initiateRequest(GetKey(id)) >> (PublicKeyRing.load _))
      // we have to look for ids matching the string, since IDs tend to be sent with lower 32 bits.
      key <- ring.publicKeys find { k => idToString(k.keyID) contains idToString(id) } 
    } yield key
  
  /** Pushes a key to the given public key server. */
  def pushKey(key: PublicKey): Unit =
    Http(initiateRequest(AddKey(key)).POST >|)
  
  // TODO - Allow search and parse format: http://keyserver.ubuntu.com:11371/pks/lookup?op=index&search=suereth
  /*
info:1:2
pub:E29DF322:1:2048:1315150989::
uid:Josh Suereth <joshua.suereth@gmail.com>:1315150989::
pub:EF5DDCCC:17:1024:1137516901::
uid:Terry Suereth (CE2008) <tsuereth@digipen.edu>:1137516901::

Note: Type bits/keyID    Date
   */
  private[this] def initiateRequest(cmd: HkpCommand): Request =
    url(serverUrl + cmd.url) <<? cmd.vars
    
  override def toString = "HkpServer(%s)" format (serverUrl)
}

object Client {
  import util.matching.Regex
  private val HkpWithPort = new Regex("hkp://(.+)(:[0-9]+)")
  private val Hkp = new Regex("hkp://(.+)/?")
  /** Creates a new HKP client that can push/pull keys from a public server. */
  def apply(url: String): Client = url match {
    case Hkp(server)               => new DispatchClient("http://%s:11371" format (server))
    case HkpWithPort(server, port) => new DispatchClient("http://%s:%s" format (server,port))
    case _                         => new DispatchClient(url)
  }
}