package slick.additions.test

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import slick.future.Database
import slick.jdbc.DatabaseConfig

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Suite}


trait TestsCommon extends BeforeAndAfter with ScalaFutures { this: Suite =>

  import TestProfile.api._


  def schema: TestProfile.DDL

  val db =
    Await.result(
      Database.open(
        DatabaseConfig.forURL(
          TestProfile,
          s"jdbc:h2:mem:${getClass.getSimpleName};DB_CLOSE_DELAY=-1",
          driver = "org.h2.Driver"
        )
      ),
      Duration.Inf
    )

  before {
    db.run(schema.create).futureValue
  }

  after {
    db.run(schema.drop).futureValue
  }
}
