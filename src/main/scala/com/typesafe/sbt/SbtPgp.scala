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
  
  /** Configuration for GPG command line */
  def gpgConfigurationSettings: Seq[Setting[_]] = 
    PgpSettings.gpgConfigurationSettings
  
  
  /** Configuration for BC JVM-local PGP */
  def nativeConfigurationSettings: Seq[Setting[_]] = 
    PgpSettings.nativeConfigurationSettings
  /** These are all the configuration related settings that are common
   * for a multi-project build, and can be re-used on
   * ThisBuild or maybe Global.
   */
  def configurationSettings: Seq[Setting[_]] =
    PgpSettings.configurationSettings
  /** Configuration for signing artifacts.  If you use new scopes for
   * packagedArtifacts, you need to add this in that scope to your build.
   */
  def signingSettings: Seq[Setting[_]] = 
    PgpSettings.signingSettings
  /** Settings used to verify signatures on dependent artifacts. */
  def verifySettings: Seq[Setting[_]] = 
    PgpSettings.verifySettings 
  /** Settings this plugin defines. TODO - require manual setting of these... */
  lazy val allSettings = PgpSettings.allSettings
  
  /** TODO - Deprecate this usage... */
  override val settings = PgpSettings.allSettings
  
  // TODO - Fix this function!
  def usePgpKeyHex(id: String) =
    pgpSigningKey := Some(new java.math.BigInteger(id, 16).longValue)
}
