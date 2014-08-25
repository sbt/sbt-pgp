pgpSecretRing := baseDirectory.value / "secring.pgp"

pgpPublicRing := baseDirectory.value / "pubring.pgp"

pgpReadOnly := false

pgpPassphrase := Some("test password".toCharArray)