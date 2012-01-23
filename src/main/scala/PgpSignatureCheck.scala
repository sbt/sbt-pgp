package com.jsuereth
package pgp

import sbt._
import Keys._
import sbt.Project.Initialize
import complete.Parser
import complete.DefaultParsers._

final case class GetSignaturesModule(id: ModuleID, modules: Seq[ModuleID], configurations: Seq[Configuration])
final case class GetSignaturesConfiguration(module: GetSignaturesModule, 
                                            configuration: UpdateConfiguration, 
                                            ivyScala: Option[IvyScala])

sealed trait SignatureCheckResult
object SignatureCheckResult {
  /** The signature is ok and we trust it. */
  case object OK extends SignatureCheckResult
  /** The dependency has no PGP signature. */
  case object MISSING extends SignatureCheckResult
  /** The dependency is ok, but we don't trust the signer. */
  case object UNTRUSTED extends SignatureCheckResult
  /** The signature is all-out bad. */
  case object BAD extends SignatureCheckResult
}

case class SignatureCheck(module: ModuleID, artifact: Artifact, result: SignatureCheckResult) {
  override def toString = "%s:%s:%s:%s [%s]" format (module.organization, module.name, module.revision, artifact.`type`, result.toString)
}

object PgpSignatureCheck {
  /** Downloads PGP signatures so we can test them. */
  def resolveSignatures(ivySbt: IvySbt, config: GetSignaturesConfiguration, log: Logger): UpdateReport = {

    def restrictedCopy(m: ModuleID, confs: Boolean) =
      ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion, extraAttributes = m.extraAttributes, configurations = if(confs) m.configurations else None)

    def signatureArtifacts(m: ModuleID): Option[ModuleID] = {
      // TODO - Some kind of filtering
      // TODO - We *can't* assume everything is a jar
      def signatureFor(artifact: Artifact) = Seq(artifact, artifact.copy(extension = artifact.extension + gpgExtension))
      // Assume no explicit artifact = "jar" artifact.
      if(m.explicitArtifacts.isEmpty) Some(m.copy(explicitArtifacts = Seq(Artifact(m.name, "jar","jar"), Artifact(m.name, "jar", "jar" + gpgExtension))))
      else Some(m.copy(explicitArtifacts = m.explicitArtifacts flatMap signatureFor))
    }
    import config.{configuration => c, module => mod, _}
    import mod.{configurations => confs, _}

    val baseModules = modules map { m => restrictedCopy(m, true) }
    val deps = baseModules.distinct flatMap signatureArtifacts
    val base = restrictedCopy(id, true)
    val module = new ivySbt.Module(InlineConfiguration(base, ModuleInfo(base.name), deps).copy(ivyScala = ivyScala, configurations = confs))
    val upConf = new UpdateConfiguration(c.retrieve, true, c.logging)
		IvyActions.update(module, upConf, log)
  }

  def checkSignaturesTask(update: UpdateReport, pgp: PgpVerifier, s: TaskStreams): Unit = {
    val results = checkArtifactSignatures(update, pgp, s) ++ missingSignatures(update,s)

     def prettify(m: ModuleID, a: Artifact) = "%s:%s:%s:%s" format (m.organization, m.name, m.revision, a.`type`)
    // TODO - Allow different log levels
    // TODO - Does sort-with for pretty print make any sense?
    s.log.info("----- PGP Signature Results -----")
    results sortWith {
      case (a, b) if a.result == b.result                         => a.toString < b.toString
      case (SignatureCheck(_,_, SignatureCheckResult.OK), _)      => true
      case (_, SignatureCheck(_,_, SignatureCheckResult.OK))      => false
      case (SignatureCheck(_,_, SignatureCheckResult.MISSING), _) => true
      case (_, SignatureCheck(_,_, SignatureCheckResult.MISSING)) => false
      case (a,b)                                                  => a.toString < b.toString
    } foreach {
      case SignatureCheck(m, a, SignatureCheckResult.OK)      => s.log.info("Signature for " + prettify(m,a)  + " [OK]")
      case SignatureCheck(m, a, SignatureCheckResult.MISSING) => s.log.warn("Signature for " + prettify(m,a)  + " [MISSING]")
      case SignatureCheck(m, a, _)                            => s.log.error("Signature for " + prettify(m,a) + " [BAD/UNTRUSTED]")
    }
    if(results exists (x => x.result == SignatureCheckResult.BAD || x.result == SignatureCheckResult.UNTRUSTED))
      sys.error("Some artifacts have bad signatures or are signed by untrusted sources!")
    ()
  }
  private def missingSignatures(update: UpdateReport, s: TaskStreams): Seq[SignatureCheck] = 
    for {
      config <- update.configurations
      module <- config.modules
      artifact <- module.missingArtifacts
      if artifact.extension endsWith gpgExtension
    } yield SignatureCheck(module.module, artifact, SignatureCheckResult.MISSING)

  private def checkArtifactSignatures(update: UpdateReport, pgp: PgpVerifier, s: TaskStreams): Seq[SignatureCheck] =
    for {
      config <- update.configurations
      module <- config.modules
      (artifact, file) <- module.artifacts
      if file.getName endsWith gpgExtension
    } yield SignatureCheck(module.module, artifact, checkArtifactSignature(file, pgp, s))

  private def checkArtifactSignature(signatureFile: File, pgp: PgpVerifier, s: TaskStreams): SignatureCheckResult = 
    if(pgp.verifySignature(signatureFile, s)) SignatureCheckResult.OK
    else SignatureCheckResult.BAD
}


