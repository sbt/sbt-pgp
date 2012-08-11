package com.jsuereth.pgp
package hkp

/** Represents a client connected to a PGP public key server. */
trait Client {
  /** Retreives a PGP Public Key from the server. */
  def getKey(id: Long): Option[PublicKeyRing]
  /** Pushes a public key to a key server. */
  def pushKey(key: PublicKey, logger: String => Unit): Unit
  /** Pushes a public key to a key server. */
  def pushKeyRing(key: PublicKeyRing, logger: String => Unit): Unit
  /** Searches for a term on the keyserver and returns all the results. */
  def search(term: String): Seq[LookupKeyResult]
}

// case class KeyIndexResult(id: String, identity: String, date: java.util.Date)

private[hkp] class DispatchClient(serverUrl: String) extends Client {
  
  import dispatch._
  import com.ning.http.client.RequestBuilder
  import util.control.Exception.catching
  
  /** Attempts to pull a public key from the HKP server.
   * @return Some(key) if successful, None otherwise.
   */
  def getKey(id: Long): Option[PublicKeyRing] =
    for {
      ring <- catching(classOf[Exception]) opt Http(
          initiateRequest(GetKey(id)) OK (r => PublicKeyRing.load(r.getResponseBodyAsStream)))()
      // we have to look for ids matching the string, since IDs tend to be sent with lower 32 bits.
      key <- ring.publicKeys find { k => idToString(k.keyID) contains idToString(id) } 
    } yield ring
  
  /** Pushes a key to the given public key server. */
  def pushKey(key: PublicKey, logger: String => Unit): Unit =
    Http(initiateFormPost(AddKey(key)) OK (as.String andThen {c => logger("received: " + c) }))()
  
  /** Pushes a key to the given public key server. */
  def pushKeyRing(key: PublicKeyRing, logger: String => Unit): Unit =
    Http(initiateFormPost(AddKey(key)) OK (as.String andThen { c => logger("received: " + c) }))()
    
  /** Searches for a term on the keyserver and returns all the results. */
  def search(term: String): Seq[LookupKeyResult] = 
    (catching(classOf[Exception]) opt 
        Client.LookupParser.parseFile(Http(
            initiateRequest(Find(term)) OK as.String)()) getOrElse Seq.empty)
  
  // TODO - Allow search and parse format: http://keyserver.ubuntu.com:11371/pks/lookup?op=index&search=suereth
  /*
info:1:2
pub:E29DF322:1:2048:1315150989::
uid:Josh Suereth <joshua.suereth@gmail.com>:1315150989::
pub:EF5DDCCC:17:1024:1137516901::
uid:Terry Suereth (CE2008) <tsuereth@digipen.edu>:1137516901::

Note: Type bits/keyID    Date
   */
  private[this] def initiateRequest(cmd: HkpCommand): RequestBuilder =
    url(serverUrl + cmd.url) <<? cmd.vars
  
  private[this] def initiateFormPost(cmd: HkpCommand): RequestBuilder =
    url(serverUrl + cmd.url).POST << cmd.vars
    
  override def toString = "HkpServer(%s)" format (serverUrl)
}

case class LookupKeyResult(id: String, time: java.util.Date, user: Seq[String])

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
  
  object LookupParser extends scala.util.parsing.combinator.RegexParsers {
    def s: Parser[String] = ":"
    def eol: Parser[String] = "[\r\n]+".r
    def pub: Parser[String] = "pub"
    def uid: Parser[String] = "uid"
    def info: Parser[String] = "info"
    def name: Parser[String] = guard(not(info | uid | pub)) ~> ("[^\\:\n\r]*".r)
    def line: Parser[Seq[String]] = (pub <~ s) ~ rep1sep(name, s) ^^ {
      case x ~ xs => x +: xs
    }
    def userId: Parser[String] = ("uid" ~ s) ~> rep1sep(name,s) ^^ {
      case Seq(name, date, _*) => name
    }
    def keyHeader: Parser[(String, java.util.Date)] = line ^? {
      case Seq("pub", name, _, _, date, _*) => (name, new java.util.Date(date.toLong * 1000L))
    }
    def key: Parser[LookupKeyResult] = keyHeader ~ rep1(userId) ^^ {
      case (id, ts) ~ users => LookupKeyResult(id, ts, users)
    }
    def infoheader = "info" ~ s ~ rep1sep(name,s)
    def queryresponse: Parser[Seq[LookupKeyResult]] = infoheader ~> rep(key)
    
    def parseFile(input: String): Seq[LookupKeyResult] = {
      println("Parsing lookup value: " + input)
      parseAll(queryresponse, input) match {
        case Success(data, _) => data
        case _                => error("Issues")
      }
    }
  }
}