// package sbt
// package sbtpgp

// trait InteractionService {
//   /** Prompts the user for input, optionally with a mask for characters. */
//   def readLine(prompt: String, mask: Boolean): Option[String]
//   /** Ask the user to confirm something (yes or no) before continuing. */
//   def confirm(msg: String): Boolean
// }

// class CommandLineUIServices extends InteractionService {
//   def readLine(prompt: String, mask: Boolean): Option[String] =
//     {
//       val maskChar = if (mask) Some('*')
//                      else None
//       SimpleReader.readLine(prompt, maskChar)
//     }

//   def confirm(msg: String): Boolean =
//     {
//       object Assent {
//         def unapply(in: String): Boolean = {
//           (in == "y" || in == "yes")
//         }
//       }
//       SimpleReader.readLine(msg + " (yes/no): ", None) match {
//         case Some(Assent()) => true
//         case _ => false
//       }
//     }
// }
