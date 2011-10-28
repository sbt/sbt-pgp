package com.jsuereth.pgp

import java.io._

/** This trait represents something that can be saved.   If the class can provide a single saveTo(OutputStream) method, then
 *  this trait provides corresponding saveToFile and saveToString methods.
 */
trait StreamingSaveable {
  /** Saves the current entity to an output stream. */
  def saveTo(output: OutputStream): Unit
  /** Saves the current entity to a file. */
  def saveToFile(file: File): Unit = saveTo(new FileOutputStream(file))
  /** Saves the current entity into a string. */
  def saveToString: String = {
    val baos = new ByteArrayOutputStream
    saveTo(baos)
    baos.toString(java.nio.charset.Charset.defaultCharset.name)
  }
}
