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
  type InteractionService = sbt.sbtpgp.InteractionService

  def pgpRequires: Plugins = sbt.plugins.IvyPlugin

  val interactionService = taskKey[InteractionService]("Service used to ask for user input through the current user interface(s).")

  def compatSettings: Vector[Def.Setting[_]] =
    Vector(
      interactionService := defaultInteraction
    )

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

  private lazy val commandLineUIServices: InteractionService = new CommandLineUIServices
  def defaultInteraction: InteractionService = commandLineUIServices
}
