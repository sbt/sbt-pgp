package sbt
package sbtpgp

import sbt.{ librarymanagement => lm }
import sbt.internal.{ librarymanagement => ilm }
import scala.annotation.{ meta, StaticAnnotation }
import Keys._
import com.jsuereth.sbtpgp.PgpKeys._
import com.jsuereth.sbtpgp.gpgExtension

object Compat {
  val IvyActions = ilm.IvyActions
  type IvySbt = ilm.IvySbt
  type IvyScala = lm.ScalaModuleInfo
  type UpdateConfiguration = lm.UpdateConfiguration
  type UnresolvedWarning = lm.UnresolvedWarning
  type UnresolvedWarningConfiguration = lm.UnresolvedWarningConfiguration
  val UnresolvedWarningConfiguration = lm.UnresolvedWarningConfiguration

  val ivyScala = Keys.scalaModuleInfo

  def pgpRequires: Plugins = sbt.plugins.IvyPlugin

  def subConfiguration(m: ModuleID, confs: Boolean): ModuleID =
    m.withConfigurations(
      if (confs) m.configurations
      else None
    )

  def subExplicitArtifacts(m: ModuleID, artifacts: Vector[Artifact]): ModuleID =
    m.withExplicitArtifacts(artifacts)

  // This hack to access private[sbt]
  def updateEither(
      module: IvySbt#Module,
      configuration: UpdateConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      logicalClock: LogicalClock,
      depDir: Option[File],
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] =
    IvyActions.updateEither(module, configuration, uwconfig, log)

  val signedArtifacts = TaskKey[Map[Artifact, File]](
    "signed-artifacts",
    "Packages all artifacts for publishing and maps the Artifact definition to the generated file."
  )

  def signingSettings0: Seq[Setting[_]] = Seq(
    // conditional
    signedArtifacts := {
      if (!(pgpSigner / skip).value) {
        val artifacts = packagedArtifacts.value
        val r = pgpSigner.value
        val s = streams.value
        artifacts flatMap {
          case (art, file) =>
            Seq(
              art -> file,
              art.withExtension(art.extension + gpgExtension) -> r
                .sign(file, new File(file.getAbsolutePath + gpgExtension), s)
            )
        }
      } else packagedArtifacts.value
    }
  )

  @meta.getter
  class cacheLevel(
      include: Array[String]
  ) extends StaticAnnotation
}
