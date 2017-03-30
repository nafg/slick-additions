package slick.additions.test

import slick.additions.KeyedTableComponent
import slick.jdbc.H2Profile

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Suite}


object driver extends H2Profile with KeyedTableComponent


trait TestsCommon extends BeforeAndAfter with ScalaFutures {
  this: Suite =>

  import driver.api._


  def schema: driver.DDL

  val db = Database.forURL(s"jdbc:h2:mem:${getClass.getSimpleName};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

  before {
    db.run(schema.create).futureValue
  }

  after {
    db.run(schema.drop).futureValue
  }
}
