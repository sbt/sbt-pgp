package com.typesafe.sbt

import com.typesafe.sbt.pgp._
import sbt._
import Keys._
import sbt.Project.Initialize
import complete.Parser
import complete.DefaultParsers._
import SbtHelpers._
import PgpKeys._

/**
 * This class is used to control what we expose to 
 * users.   It grants access to all our keys in the
 * common naming sense of plugins.   This is temporary
 * until we clean this plugin up for 0.12.0 usage.
 */
object SbtPgp extends Plugin {
  
  val PgpKeys = pgp.PgpKeys
  
  // TODO - Are these ok for style guide?  We think so.
  def useGpg = PgpKeys.useGpg in Global
  def useGpgAgent = PgpKeys.useGpgAgent in Global
  def pgpSigningKey = PgpKeys.pgpSigningKey in Global
  def pgpPassphrase = PgpKeys.pgpPassphrase in Global
  def pgpReadOnly = PgpKeys.pgpReadOnly in Global
  def pgpPublicRing = PgpKeys.pgpPublicRing in Global
  def pgpSecretRing = PgpKeys.pgpSecretRing in Global
  
  /** TODO - Maybe we should opt-in to this usage, for signing. */
  override val settings = PgpSettings.projectSettings
  def signingSettings = PgpSettings.signingSettings
  override val buildSettings = PgpSettings.globalSettings
  
  // TODO - Fix this function!
  def usePgpKeyHex(id: String) =
    pgpSigningKey := Some(new java.math.BigInteger(id, 16).longValue)
}
