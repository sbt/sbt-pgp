package growl

import sbt._
import Keys._
import sbt.Project.Initialize

object GpgPlugin extends Plugin {

  lazy val GPG = config("gpg")

  lazy val gpgCommand = SettingKey[String]("gpg-command", "The path of the GPG command to run")
  lazy val gpgRunner = TaskKey[GpgRunner]("gpg-runner", "The helper class to run GPG commands.")

  // TODO - home dir, use-agent, 
  // TODO - --batch and pasphrase and read encrypted passphrase...
  // TODO --local-user
  // TODO --detach-sign
  // TODO --armor
  // TODO --no-tty
  // TODO  Signature filename
  
  override val settings = Seq(
    skip in GPG := false,
    gpgCommand in GPG := (if(isWindows) "gpg.exe" else "gpg"),
    gpgRunner in GPG <<= gpgCommand in GPG map (new GpgRunner(_)),
    packagedArtifacts <<= (packagedArtifacts, gpgRunner in GPG, skip in GPG, streams) map {
      (artifacts, r, skipZ, s) =>
        if (!skipZ) {
          artifacts flatMap {
            case (art, file) =>
              Seq(art                                          -> file, 
                  art.copy(extension = art.extension + ".asc") -> r.sign(file))
          }
        } else artifacts
    }
  )
  
  def isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1

  def signArtifacts(artifacts: Iterable[File], runner: GpgRunner, s: TaskStreams): Iterable[File] = 
    artifacts map runner.sign

  // TODO - Add all features here, like key rings and such.
  class GpgRunner(command: String) {
    /** Signs an input file, returns the signature file. */
    def sign(file: File): File = {
       val signature = new File(file.getAbsolutePath + ".asc")
       if (signature.exists) IO.delete(signature)
       // --output = sig file
       Process(command, Seq("--detach-sign", "--output", signature.getAbsolutePath, file.getAbsolutePath)).!
       signature
    }
  }
}
