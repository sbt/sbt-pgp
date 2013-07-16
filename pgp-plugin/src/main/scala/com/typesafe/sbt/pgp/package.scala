package com.typesafe.sbt

import sbt._

package object pgp {
  /** Default extension for PGP signatures. */
  val gpgExtension = ".asc"

  /** Reads the passphrase from the console. */
  def readPassphrase(): Array[Char] = System.out.synchronized {
    (SimpleReader.readLine("Please enter your PGP passphrase> ", Some('*')) getOrElse error("No password provided.")).toCharArray
  }
  
  // Helper to figure out how to run GPG signing...
  def isWindows = System.getProperty("os.name").toLowerCase.indexOf("windows") != -1
}
