package sbt
package sbtpgp

import sbt.{ librarymanagement => lm }
import sbt.internal.{ librarymanagement => ilm }

object Compat {
  val IvyActions = ilm.IvyActions
  type PublishConfiguration = ilm.PublishConfiguration
  type IvySbt = ilm.IvySbt
  type IvyScala = lm.IvyScala
  type UpdateConfiguration = lm.UpdateConfiguration
  type InlineConfiguration = ilm.InlineConfiguration
  val InlineConfiguration = ilm.InlineConfiguration
  val defaultProgress = EvaluateTask.defaultProgress
  type UnresolvedWarning = ilm.UnresolvedWarning
  type UnresolvedWarningConfiguration = ilm.UnresolvedWarningConfiguration
  val UnresolvedWarningConfiguration = ilm.UnresolvedWarningConfiguration
  type LogicalClock = ilm.LogicalClock
  val LogicalClock = ilm.LogicalClock
  val CommandLineUIServices = sbt.CommandLineUIService

  def pgpRequires: Plugins = sbt.plugins.IvyPlugin

  def subConfiguration(m: ModuleID, confs: Boolean): ModuleID =
    m.withConfigurations(
      if (confs) m.configurations
      else None
    )

  def subExplicitArtifacts(m: ModuleID, artifacts: Vector[Artifact]): ModuleID =
    m.withExplicitArtifacts(artifacts)

  def subExtension(art: Artifact, ext: String): Artifact =
    art.withExtension(ext)

  def subMissingOk(c: UpdateConfiguration, ok: Boolean): UpdateConfiguration =
    c.withMissingOk(ok)

  def mkInlineConfiguration(base: ModuleID, deps: Vector[ModuleID],
    ivyScala: Option[IvyScala], confs: Vector[Configuration]): InlineConfiguration =
    InlineConfiguration(false, None, base, ModuleInfo(base.name), deps)
    .withIvyScala(ivyScala)
    .withConfigurations(confs)

  def updateEither(
    module: IvySbt#Module,
    configuration: UpdateConfiguration,
    uwconfig: UnresolvedWarningConfiguration,
    logicalClock: LogicalClock,
    depDir: Option[File],
    log: Logger): Either[UnresolvedWarning, UpdateReport] =
    IvyActions.updateEither(module, configuration, uwconfig, logicalClock, depDir, log)
}
