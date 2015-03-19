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
object SbtPgp extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = sbt.plugins.InteractionServicePlugin && sbt.plugins.IvyPlugin
  
  // Note - workaround for issues in sbt 0.13.5 autoImport
  object autoImportImpl {

    val PgpKeys = pgp.PgpKeys
  
    // TODO - Are these ok for style guide?  We think so.
    def useGpg = PgpKeys.useGpg in Global
    def useGpgAgent = PgpKeys.useGpgAgent in Global
    def pgpSigningKey = PgpKeys.pgpSigningKey in Global
    def pgpPassphrase = PgpKeys.pgpPassphrase in Global
    def pgpReadOnly = PgpKeys.pgpReadOnly in Global
    def pgpPublicRing = PgpKeys.pgpPublicRing in Global
    def pgpSecretRing = PgpKeys.pgpSecretRing in Global

    // TODO - Fix this function!
    def usePgpKeyHex(id: String) =
      pgpSigningKey := Some(new java.math.BigInteger(id, 16).longValue)

    def signingSettings = PgpSettings.signingSettings
  }
  val autoImport = autoImportImpl
  // TODO - Maybe signing settigns should be a different plugin...
  override val projectSettings = PgpSettings.projectSettings
  override val buildSettings = PgpSettings.globalSettings
  
  
}
