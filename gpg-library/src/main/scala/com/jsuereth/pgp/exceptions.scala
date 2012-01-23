package com.jsuereth.pgp

trait PgpException extends Exception

/** Exception thrown when a key is not found in the keystore. */
case class KeyNotFoundException(id: Long) extends Exception("Could not find PGP key ["+id+"]") with PgpException