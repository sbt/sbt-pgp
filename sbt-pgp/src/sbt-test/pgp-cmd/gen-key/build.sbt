import com.jsuereth.sbt.pgp.{SbtPgpCommandContext, PgpKeys}

pgpSecretRing := baseDirectory.value / "secring.pgp"

pgpPublicRing := baseDirectory.value / "pubring.pgp"

pgpReadOnly := false

pgpPassphrase in Global := Some("test password".toCharArray)

PgpKeys.pgpCmdContext in Global := {
  // Override the command context so we force in some text input.
  new SbtPgpCommandContext(PgpKeys.pgpStaticContext.value, PgpKeys.pgpPassphrase.value, streams.value) {
    override def readInput(msg: String): String = {
      "Test"
    }
    override def readHidden(msg: String): String = "test password"
  }
}

scalaVersion := "2.10.4"

name := "test"

organization := "test"

version := "1.0"

useGpg in Global := false
useGpgAgent in Global := false
useGpgPinentry in Global := false
