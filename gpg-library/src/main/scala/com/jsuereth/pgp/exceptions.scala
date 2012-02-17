package com.jsuereth.pgp

/** Base class for exceptions thrown in PGP library. */
trait PgpException extends Exception

/** Exception thrown when a key is not found in the keystore. */
case class KeyNotFoundException(id: Long) extends Exception("Could not find PGP key [%x]" format (id)) with PgpException

case class NotEncryptedMessageException(msg: String) extends Exception(msg) with PgpException

case class IntegrityException(msg: String) extends Exception(msg) with PgpException