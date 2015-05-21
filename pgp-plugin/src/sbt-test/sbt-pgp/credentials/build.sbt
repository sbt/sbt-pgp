pgpSecretRing := baseDirectory.value / "secring.pgp"

pgpPublicRing := baseDirectory.value / "pubring.pgp"

credentials in GlobalScope := Seq(Credentials("", "pgp", "", "test password"))

scalaVersion := "2.10.4"

name := "test"

organization := "test"

version := "1.0"