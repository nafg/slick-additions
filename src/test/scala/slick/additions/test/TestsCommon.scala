package slick.additions.test

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Suite}


trait TestsCommon extends BeforeAndAfter with ScalaFutures { this: Suite =>

  import TestProfile.api._


  def schema: TestProfile.DDL

  val db = Database.forURL(s"jdbc:h2:mem:${getClass.getSimpleName};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

  before {
    db.run(schema.create).futureValue
  }

  after {
    db.run(schema.drop).futureValue
  }
}
