package com.jsuereth.pgp
package hkp

import java.nio.ByteBuffer
import java.io.InputStream
import scala.concurrent.Future

/** Represents a client connected to a PGP public key server. */
trait Client {
  /** Retreives a PGP Public Key from the server. */
  def getKey(id: Long): Future[PublicKeyRing]
  /** Pushes a public key to a key server. */
  def pushKey(key: PublicKey, logger: String => Unit): Unit
  /** Pushes a public key to a key server. */
  def pushKeyRing(key: PublicKeyRing, logger: String => Unit): Unit
  /** Searches for a term on the keyserver and returns all the results. */
  def search(term: String): Future[Vector[LookupKeyResult]]
}

// case class KeyIndexResult(id: String, identity: String, date: java.util.Date)

private[hkp] class GigahorseClient(serverUrl: String) extends Client {
  import gigahorse._, support.okhttp.Gigahorse
  import util.control.Exception.catching
  import scala.concurrent.ExecutionContext.Implicits._
  val http = Gigahorse.http(Gigahorse.config)
  def asInputStream: FullResponse => InputStream = (r: FullResponse) =>
    new ByteBufferBackedInputStream(r.bodyAsByteBuffer)

  /** Attempts to pull a public key from the HKP server.
   * @return Some(key) if successful, None otherwise.
   */
  def getKey(id: Long): Future[PublicKeyRing] =
    for {
      ring <- http.run(initiateRequest(GetKey(id)), asInputStream andThen PublicKeyRing.load)
      key  <- findId(ring, id)
    } yield ring

  // we have to look for ids matching the string, since IDs tend to be sent with lower 32 bits.
  def findId(ring: PublicKeyRing, id: Long): Future[PublicKey] =
    (ring.publicKeys find { k =>
      idToString(k.keyID) contains idToString(id)
    }) match {
      case Some(x) => Future.successful(x)
      case _       => Future.failed(new RuntimeException(s"Key $id was not found."))
    }

  /** Pushes a key to the given public key server. */
  def pushKey(key: PublicKey, logger: String => Unit): Unit =
    http.run(initiateFormPost(AddKey(key)),
      Gigahorse.asString andThen { c: String => logger("received: " + c) })

  /** Pushes a key to the given public key server. */
  def pushKeyRing(key: PublicKeyRing, logger: String => Unit): Unit =
    http.run(initiateFormPost(AddKey(key)),
      Gigahorse.asString andThen { c: String => logger("received: " + c) })

  /** Searches for a term on the keyserver and returns all the results. */
  def search(term: String): Future[Vector[LookupKeyResult]] =
    http.run(initiateRequest(Find(term)),
      Gigahorse.asString andThen { s: String => Client.LookupParser.parse(s) })
      .recover {
        case _ => Vector()
      }

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
    Gigahorse.url(serverUrl + cmd.url).addQueryString(cmd.vars.toList: _*)

  private[this] def initiateFormPost(cmd: HkpCommand): Request =
    Gigahorse.url(serverUrl + cmd.url).post(cmd.vars map { case (k, v) =>
      k -> List(v)
    })

  override def toString = "HkpServer(%s)" format (serverUrl)
}

private class ByteBufferBackedInputStream(buffer: ByteBuffer) extends InputStream {
  override def read: Int =
    if (!buffer.hasRemaining) -1
    else buffer.get & 0xFF

  override def read(bytes: Array[Byte], offset: Int, len: Int): Int =
    if (!buffer.hasRemaining) -1
    else {
      val len1 = Math.min(len, buffer.remaining)
      buffer.get(bytes, offset, len1)
      len1
    }
}

case class LookupKeyResult(id: String, time: java.util.Date, user: Seq[String])

object Client {
  import util.matching.Regex
  private val HkpWithPort = new Regex("hkp://(.+)(:[0-9]+)")
  private val Hkp = new Regex("hkp://(.+)/?")
  /** Creates a new HKP client that can push/pull keys from a public server. */
  def apply(url: String): Client = url match {
    case Hkp(server)               => new GigahorseClient("http://%s:11371" format (server))
    case HkpWithPort(server, port) => new GigahorseClient("http://%s:%s" format (server,port))
    case _                         => new GigahorseClient(url)
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

    def parse(input: String): Vector[LookupKeyResult] = {
      println("Parsing lookup value: " + input)
      parseAll(queryresponse, input) match {
        case Success(data, _) => data.toVector
        case _                => sys.error("Issues")
      }
    }
  }
}