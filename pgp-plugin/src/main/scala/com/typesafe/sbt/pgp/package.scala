package com.typesafe.sbt

import sbt._

package object pgp {
  /** Default extension for PGP signatures. */
  val gpgExtension = ".asc"
  
  // Helper to figure out how to run GPG signing...
  def isWindows = System.getProperty("os.name").toLowerCase.indexOf("windows") != -1
}
