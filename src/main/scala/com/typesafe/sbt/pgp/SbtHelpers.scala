package com.typesafe.sbt
package pgp

import sbt._
import Project.Initialize

object SbtHelpers {
  /** Initializes a setting with a given value if it isn't already configured. */
  def initIf[T](key: SettingKey[T], value: => T): Setting[T] =
    key <<= key ?? value
  /** Helper method to switch between two initializers based on
   * the value of the switch setting.
   */
  def switch[T](switch: SettingKey[Boolean],
                           iftrue: Initialize[T],
                           iffalse: Initialize[T]): Initialize[T] =
    switch.zipWith(iftrue) { (use, first) =>
      if(use) Some(first) else None
    }.zipWith(iffalse) { (opt, second) => opt getOrElse second }
}

/** Simple caching api.  So simple it's probably horribly bad in some way.
 * OH, right... synchronization could be bad here...
 */
trait Cache[K,V] {
  private val cache = new collection.mutable.HashMap[K, V]
  /** This method attempts to use a cached value, if one is found.  If
   * there is no cached value, the default is used and placed
   * back into the cache.
   * 
   * Upon any exception, the cache is cleared.
   * 
   * TODO - Allow subclasses to handle specific exceptions.
   */
  @inline
  final def withValue[U](key: K, default: => V)(f: V => U): U = {
    try {
      f(synchronized(cache get key getOrElse {
        val pw = default
        cache.put(key, pw)
        pw
      }))
    } catch {
      case t: Exception =>
        // Clear the cache on any exception
        synchronized(cache remove key)
        throw t
    }
  }
}

// TODO - Less ugly/dangerous hack here... 
//  - Expire passwords after N minutes etc.
//  - Kill password only on password exceptions.
private[pgp] object PasswordCache extends Cache[String, Array[Char]]