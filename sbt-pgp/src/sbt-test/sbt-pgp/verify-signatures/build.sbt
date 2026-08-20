@transient
lazy val check = taskKey[Unit]("")

@transient
lazy val checkGuard = taskKey[Unit]("")

@transient
lazy val checkVerify = taskKey[Unit]("")

scalaVersion := "2.13.2"
name := "test"
organization := "test"
version := "1.0"
publish / skip := true

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.2.0"

// We don't have the real signers' public keys imported in this environment, so stub out
// verification with one that always trusts what updatePgpSignatures resolved. This lets us
// exercise checkPgpSignatures' own plumbing (turning an UpdateReport into a SignatureCheckReport)
// deterministically, without depending on an external keyserver or a bundled keyring.
PgpKeys.pgpVerifierFactory := new com.jsuereth.sbtpgp.PgpVerifierFactory {
  def withVerifier[T](f: com.jsuereth.sbtpgp.PgpVerifier => T): T =
    f(new com.jsuereth.sbtpgp.PgpVerifier {
      def verifySignature(file: File, s: TaskStreams) = com.jsuereth.sbtpgp.SignatureCheckResult.OK
    })
}

check := {
  val report = PgpKeys.updatePgpSignatures.value
  val log = streams.value.log

  val resolved = for {
    conf <- report.configurations
    mod <- conf.modules
    (art, file) <- mod.artifacts
    if art.extension.endsWith(".asc")
  } yield mod.module -> art

  val missing = for {
    conf <- report.configurations
    mod <- conf.modules
    art <- mod.missingArtifacts
    if art.extension.endsWith(".asc")
  } yield mod.module -> art

  log.info(s"Resolved ${resolved.size} PGP signature artifact(s), missing ${missing.size}")
  resolved.foreach { case (m, a) => log.info(s"  resolved: $m $a") }
  missing.foreach { case (m, a) => log.info(s"  missing:  $m $a") }

  assert(
    resolved.nonEmpty,
    "Expected at least one .asc PGP signature artifact to be resolved via the Coursier-backed LM API (dependencyResolution)"
  )
  assert(
    resolved.exists { case (m, _) => m.name == "scala-xml_2.13" },
    s"Expected scala-xml_2.13's PGP signature to be resolved, got: ${resolved.map(_._1)}"
  )
}

// Regression guard: resolveSignatures should fail loudly rather than silently returning an
// empty report when asked about a configuration the resolver doesn't recognize (e.g. Ivy's
// "default" configuration, which the Coursier-backed resolver has no mapping for).
checkGuard := {
  val dr = dependencyResolution.value
  val badModule =
    com.jsuereth.sbtpgp.GetSignaturesModule(projectID.value, libraryDependencies.value, Configurations.Default :: Nil)
  val badConfig =
    com.jsuereth.sbtpgp.GetSignaturesConfiguration(badModule, updateConfiguration.value, scalaModuleInfo.value)
  val log = streams.value.log

  val threw =
    try {
      com.jsuereth.sbtpgp.PgpSignatureCheck.resolveSignatures(dr, badConfig, log)
      false
    } catch {
      case e: RuntimeException =>
        log.info(s"Got expected failure for unrecognized configuration: ${e.getMessage}")
        true
    }

  assert(threw, "Expected resolveSignatures to fail for an unrecognized configuration, but it succeeded silently")
}

checkVerify := {
  val report = PgpKeys.checkPgpSignatures.value
  val log = streams.value.log

  log.info(s"checkPgpSignatures produced ${report.results.size} result(s)")
  report.results.foreach(r => log.info(s"  $r"))

  assert(report.results.nonEmpty, "Expected checkPgpSignatures to produce at least one result")
  assert(
    report.results.exists(_.module.name == "scala-xml_2.13"),
    s"Expected a signature check result for scala-xml_2.13, got: ${report.results.map(_.module)}"
  )
  assert(
    report.results.forall(_.result == com.jsuereth.sbtpgp.SignatureCheckResult.OK),
    s"Expected all signature checks to report OK with the stub verifier, got: ${report.results}"
  )
}
