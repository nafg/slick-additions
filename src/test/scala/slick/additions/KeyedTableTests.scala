package slick.additions

import scala.concurrent.ExecutionContext.Implicits.global

import slick.additions.test.TestsCommon
import slick.additions.test.driver.api._

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{FunSuite, Matchers}


class KeyedTableTests extends FunSuite with Matchers with TestsCommon with IntegrationPatience {
  case class Phone(kind: String, number: String, person: Option[People.Lookup] = None)
  class Phones(tag: Tag) extends EntityTable[Long, Phone](tag, "phones") {
    def tableQuery = Phones
    def person = column[Option[People.Lookup]]("person_id")
    def kind = column[String]("kind")
    def number = column[String]("number")
    def mapping = (kind, number, person) <-> (_ => Phone.tupled, Phone.unapply)
  }
  object Phones extends EntTableQuery[Long, Phone, Phones](new Phones(_))

  case class Person(first: String, last: String)
  class People(tag: Tag) extends EntityTable[Long, Person](tag, "people") {
    def tableQuery = People

    def first = column[String]("first")
    def last = column[String]("last")

    def mapping = (first, last) <-> (_ => Person.tupled, Person.unapply)
  }

  object People extends EntTableQuery[Long, Person, People](new People(_))

  val schema = Phones.schema ++ People.schema


  test("EntTableQuery#insert(KeylessEntity) does not insert twice [regression]") {
    val phone = Phone("main", "1407124383")
    val countAction = Phones.filter(_.number === phone.number).length.result
    assert(db.run(countAction).futureValue == 0)
    assert(db.run(Phones.insert(phone) >> countAction).futureValue == 1)
  }

  test("Using lookup in a query") {
    val res = db run (for {
      id <- People.map(_.mapping) returning People.map(_.key) += Person("first1", "last1")
      _ <- Phones.map(_.mapping) += Phone("M", "111", Some(People.Lookup(id)))
      xs <- People.filter(_.lookup in Phones.map(_.person)).result
    } yield xs)
    res.futureValue
  }

  test("lookup.? doesn't crash") {
    People.map(_.lookup.?)
  }

  test("lookup.inSet with non-empty doesn't crash") {
    People.map(_.lookup.inSet(Seq(People.Lookup(1L))))
  }
}
