package scala.slick.additions

import slick.session.{DatabaseCapabilities, Session}
import slick.SlickException

/**
 * A `Session` that wraps an existing `java.sql.Connection`
 */
class ConnectionSession(val conn: java.sql.Connection) extends Session {
  def metaData = conn.getMetaData

  val capabilities = new DatabaseCapabilities(this)

  var open: Boolean = !conn.isClosed

  def close() {
    if(open) conn.close()
  }

  var doRollback = false

  def rollback() {
    if(conn.getAutoCommit) throw new SlickException("Cannot roll back session in auto-commit mode")
    doRollback = true
  }

  var inTransaction = false

  def withTransaction[T](f: => T): T = if(inTransaction) f else {
    conn.setAutoCommit(false)
    inTransaction = true
    try {
      var done = false
      try {
        doRollback = false
        val res = f
        if(doRollback) conn.rollback()
        else conn.commit()
        done = true
        res
      } finally if(!done) conn.rollback()
    } finally {
      conn.setAutoCommit(true)
      inTransaction = false
    }
  }
}
