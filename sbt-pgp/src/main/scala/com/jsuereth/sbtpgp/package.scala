package com.jsuereth

package object sbtpgp {

  /** Default extension for PGP signatures. */
  val gpgExtension = ".asc"

  // Helper to figure out how to run GPG signing...
  def isWindows = System.getProperty("os.name").toLowerCase.indexOf("windows") != -1
}
