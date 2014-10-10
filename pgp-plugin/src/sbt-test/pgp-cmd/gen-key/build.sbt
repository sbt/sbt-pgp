import com.typesafe.sbt.pgp.{SbtPgpCommandContext, PgpKeys}

pgpSecretRing := baseDirectory.value / "secring.pgp"

pgpPublicRing := baseDirectory.value / "pubring.pgp"

pgpReadOnly := false

pgpPassphrase := Some("test password".toCharArray)

PgpKeys.pgpCmdContext := {
  // Override the command context so we force in some text input.
  new SbtPgpCommandContext(PgpKeys.pgpStaticContext.value, PgpKeys.pgpPassphrase.value, streams.value) {
    override def readInput(msg: String): String = {
      "Test"
    }
    override def readHidden(msg: String): String = "pw"
  }
}