package sbt

import sbt.plugins.CommandLineUIServices

object HackInteractionAccess {
  def defaultInteraction: InteractionService = CommandLineUIServices
}
