package com.jsuereth.sbtpgp

import sbt._
import sbt.sbtpgp.Compat._

/**
 * This class is used to control what we expose to
 * users.   It grants access to all our keys in the
 * common naming sense of plugins.   This is temporary
 * until we clean this plugin up for 0.12.0 usage.
 */
object SbtPgp extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = pgpRequires

  object autoImport {
    val PgpKeys = com.jsuereth.sbtpgp.PgpKeys

    // TODO - Are these ok for style guide?  We think so.
    @deprecated("useGpg is true by default; Bouncy Castle mode is deprecated", "2.0.0")
    def useGpg = (Global / PgpKeys.useGpg)
    def useGpgAgent = (Global / PgpKeys.useGpgAgent)
    def useGpgPinentry = (Global / PgpKeys.useGpgPinentry)
    def pgpSigningKey = (Global / PgpKeys.pgpSigningKey)
    def pgpPassphrase = (Global / PgpKeys.pgpPassphrase)
    def pgpKeyRing = (Global / PgpKeys.pgpKeyRing)
    def pgpPublicRing = (Global / PgpKeys.pgpPublicRing)
    def pgpSecretRing = (Global / PgpKeys.pgpSecretRing)

    def usePgpKeyHex(id: String) = pgpSigningKey := Some(id)
    def signingSettings = PgpSettings.signingSettings
  }
  // TODO - Maybe signing settings should be a different plugin...
  override val projectSettings = PgpSettings.projectSettings
  override val globalSettings = PgpSettings.globalSettings
}
