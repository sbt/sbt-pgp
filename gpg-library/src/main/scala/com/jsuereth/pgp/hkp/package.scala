package com.jsuereth.pgp


package object hkp {
  // TODO - Convert to an integer first if the Long doesn't use its upper bigs.
  def idToString(l: Long) = "%x" format l
  // TODO - Is this correct?
  def stringToId(s: String): Long = java.lang.Long.parseLong(s, 16)
}