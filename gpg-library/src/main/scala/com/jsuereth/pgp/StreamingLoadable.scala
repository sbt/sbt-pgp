package com.jsuereth.pgp

import java.io._

/** This trait is for companion objects that have objects which can streamed in.
 */
trait StreamingLoadable[T] { 
  /** Loads a {T} from an input stream. */
  def load(input: InputStream): T
  /** Loads a {T} from a file. */
  def loadFromFile(file: File): T = load(new FileInputStream(file))
  /** Loads a {T} from a string. */
  def loadFromString(input: String): T = load(new ByteArrayInputStream(input.getBytes))
}
