package sbt
package sbtpgp

import sbt.plugins.CommandLineUIServices

object Compat {
  type PublishConfiguration = sbt.PublishConfiguration
  val defaultProgress = EvaluateTask.defaultProgress
  type InteractionService = sbt.InteractionService
  def defaultInteraction: InteractionService = CommandLineUIServices
  val interactionService = InteractionServiceKeys.interactionService

  def pgpRequires: Plugins = sbt.plugins.IvyPlugin && sbt.plugins.InteractionServicePlugin

  def compatSettings: Vector[Def.Setting[_]] = Vector()

  def subConfiguration(m: ModuleID, confs: Boolean): ModuleID =
    m.copy(configurations = if (confs) m.configurations else None)

  def subExplicitArtifacts(m: ModuleID, artifacts: Vector[Artifact]): ModuleID =
    m.copy(explicitArtifacts = artifacts)

  def subExtension(art: Artifact, ext: String): Artifact =
    art.copy(extension = ext)

  def subMissingOk(c: UpdateConfiguration, ok: Boolean): UpdateConfiguration =
    c.copy(missingOk = ok)

  def mkInlineConfiguration(base: ModuleID, deps: Vector[ModuleID],
    ivyScala: Option[IvyScala], confs: Vector[Configuration]): InlineConfiguration =
    InlineConfiguration(base, ModuleInfo(base.name), deps).copy(ivyScala = ivyScala, configurations = confs)

  implicit def log2ProcessLogger(log: Logger): sys.process.ProcessLogger =
    new BufferedLogger(new FullLogger(log)) with sys.process.ProcessLogger {
      def err(s: => String): Unit = error(s)
      def out(s: => String): Unit = info(s)
    }
}
