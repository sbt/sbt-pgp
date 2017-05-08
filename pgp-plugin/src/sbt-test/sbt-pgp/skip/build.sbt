lazy val root = (project in file("."))
  .settings(
    credentials in GlobalScope := Seq(Credentials("", "pgp", "", "test password")),
    pgpSecretRing := baseDirectory.value / "secring.pgp",
    pgpPublicRing := baseDirectory.value / "pubring.pgp",
    scalaVersion := "2.10.4",
    name := "test",
    organization := "test",
    version := "1.0",
    skip in publish := true
  )
