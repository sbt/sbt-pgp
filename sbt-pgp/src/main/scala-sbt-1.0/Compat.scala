package sbt
package sbtpgp

import sbt.{ librarymanagement => lm }
import sbt.internal.{ librarymanagement => ilm }
import Keys._

object Compat {
  val IvyActions = ilm.IvyActions
  type IvySbt = ilm.IvySbt
  type IvyScala = lm.ScalaModuleInfo
  type UpdateConfiguration = lm.UpdateConfiguration
  val defaultProgress = EvaluateTask.defaultProgress
  type UnresolvedWarning = lm.UnresolvedWarning
  type UnresolvedWarningConfiguration = lm.UnresolvedWarningConfiguration
  val UnresolvedWarningConfiguration = lm.UnresolvedWarningConfiguration
  val CommandLineUIServices = sbt.CommandLineUIService
  type PublishConfiguration = lm.PublishConfiguration
  type ConfigRef = lm.ConfigRef
  val ConfigRef = lm.ConfigRef

  val ivyScala = Keys.scalaModuleInfo

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

  def mkInlineConfiguration(
      base: ModuleID,
      deps: Vector[ModuleID],
      ivyScala: Option[IvyScala],
      confs: Vector[Configuration]
  ): InlineConfiguration =
    ModuleDescriptorConfiguration(base, ModuleInfo(base.name))
      .withDependencies(deps)
      .withScalaModuleInfo(ivyScala)
      .withConfigurations(confs)

  def updateEither(
      module: IvySbt#Module,
      configuration: UpdateConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      logicalClock: LogicalClock,
      depDir: Option[File],
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] =
    IvyActions.updateEither(module, configuration, uwconfig, log)

  private val signedArtifacts = TaskKey[Map[Artifact, File]](
    "signed-artifacts",
    "Packages all artifacts for publishing and maps the Artifact definition to the generated file."
  )
  private val pgpMakeIvy = TaskKey[Option[File]]("pgpMakeIvy", "Generates the Ivy file.")

  def publishSignedConfigurationTask = Def.task {
    val _ = pgpMakeIvy.value
    Classpaths.publishConfig(
      publishMavenStyle.value,
      deliverPattern(crossTarget.value),
      if (isSnapshot.value) "integration" else "release",
      ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
      signedArtifacts.value.toVector,
      checksums = (publish / checksums).value.toVector,
      resolverName = Classpaths.getPublishTo(publishTo.value).name,
      logging = ivyLoggingLevel.value,
      overwrite = publishConfiguration.value.overwrite
    )
  }

  def publishLocalSignedConfigurationTask = Def.task {
    val _ = deliverLocal.value
    Classpaths.publishConfig(
      publishMavenStyle.value,
      deliverPattern(crossTarget.value),
      if (isSnapshot.value) "integration" else "release",
      ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
      signedArtifacts.value.toVector,
      (publishLocal / checksums).value.toVector,
      resolverName = "local",
      logging = ivyLoggingLevel.value,
      overwrite = publishConfiguration.value.overwrite
    )
  }

  def deliverPattern(outputPath: File): String =
    (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath
}
