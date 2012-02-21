package com.jsuereth.pgp.sbtplugin

import sbt._
import Project.Initialize

object SbtHelpers {
  /** Initializes a setting with a given value if it isn't already configured. */
  def initIf[T](key: SettingKey[T])(t: => T): Setting[T] =
    key <<= key ?? t
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