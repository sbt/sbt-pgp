package com.github.jsuereth.gpg

import sbt._
import Keys._
import sbt.Project.Initialize

/** The interface used to sign plugins. */
trait GpgSigner {
  def sign(file: File, signatureFile: File): File
}

/** A GpgSigner that uses the command-line to run gpg. */
class CommandLineGpgSigner(command: String) extends GpgSigner {
  def sign(file: File, signatureFile: File): File = {
      if (signatureFile.exists) IO.delete(signatureFile)
       // --output = sig file
       Process(command, Seq("--detach-sign", "--output", signatureFile.getAbsolutePath, file.getAbsolutePath)).!
       signatureFile 
  }
}
/** A GpgSigner that uses bouncy castle. */
class BouncyCastleGpgSigner(secretKeyRingFile: File, passPhrase: Array[Char]) extends GpgSigner {
  lazy val secring = BouncyCastle.loadSecretKeyRing(secretKeyRingFile)
  def sign(file: File, signatureFile: File): File = {
    if (signatureFile.exists) IO.delete(signatureFile)
    if (!signatureFile.getParentFile.exists) IO.createDirectory(signatureFile.getParentFile)
    secring.secretKey.sign(file, signatureFile, passPhrase)
  }
}

object GpgPlugin extends Plugin {
  val gpgCommand = SettingKey[String]("gpg-command", "The path of the GPG command to run")
  val gpgRunner = TaskKey[GpgSigner]("gpg-runner", "The helper class to run GPG commands.")  
  val gpgSecretRing = SettingKey[File]("gpg-secret-ring", "The location of the secret key ring.  Only needed if using bouncy castle.")
  val gpgPassphrase = SettingKey[Option[Array[Char]]]("gpg-passphrase", "The passphrase associated with the secret used to sign artifacts.")

  // TODO - home dir, use-agent, 
  // TODO - --batch and pasphrase and read encrypted passphrase...
  // TODO --local-user
  // TODO --detach-sign
  // TODO --armor
  // TODO --no-tty
  // TODO  Signature extension
  private[this] val gpgExtension = ".asc"
  
  override val settings = Seq(
    SettingKey[Boolean]("skip") in gpgRunner := false,
    gpgCommand := (if(isWindows) "gpg.exe" else "gpg"),
    gpgPassphrase := None,
    gpgSecretRing := file(System.getProperty("user.home")) / ".gnupg" / "secring.gpg",
    // If the user isn't using GPG, we'll use a bouncy-castle ring.
    gpgSecretRing <<= gpgSecretRing apply {
      case f if f.exists => f
      case _ => file(System.getProperty("user.home")) / ".sbt" / "gpg" / "secring.bc"
    },
    // TODO - Select the runner based on the existence of the gpg/gpg.exe command.
    gpgRunner <<= gpgCommand map (new CommandLineGpgSigner(_)),
    packagedArtifacts <<= (packagedArtifacts, gpgRunner, SettingKey[Boolean]("skip") in gpgRunner, streams) map {
      (artifacts, r, skipZ, s) =>
        if (!skipZ) {
          artifacts flatMap {
            case (art, file) =>
              Seq(art                                                -> file, 
                  art.copy(extension = art.extension + gpgExtension) -> r.sign(file, new File(file.getAbsolutePath + gpgExtension)))
          }
        } else artifacts
    }
  )
  // Helper to figure out how to run GPG signing...
  def isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1
}
