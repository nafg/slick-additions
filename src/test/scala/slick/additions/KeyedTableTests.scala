package slick.additions

import scala.concurrent.ExecutionContext.Implicits.global

import slick.additions.entity.EntityKey
import slick.additions.test.TestProfile.api._
import slick.additions.test.TestsCommon

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers


case class Phone(kind: String, number: String, person: Option[People.Lookup] = None)
class Phones(tag: Tag) extends EntityTable[Long, Phone](tag, "phones")           {
  def tableQuery = Phones
  def person     = column[Option[People.Lookup]]("person_id")
  def kind       = column[String]("kind")
  def number     = column[String]("number")
  def mapping    = (kind, number, person).mapTo[Phone]
}
object Phones          extends EntTableQuery[Long, Phone, Phones](new Phones(_)) {
  def countAction(phone: Phone) = Phones.filter(_.number === phone.number).length.result
}

case class Person(first: String, last: String)
class People(tag: Tag) extends EntityTable[Long, Person](tag, "people") {
  def tableQuery = People

  def first = column[String]("first")
  def last  = column[String]("last")

  def mapping = (first, last).mapTo[Person]
}
object People          extends EntTableQuery[Long, Person, People](new People(_))

class KeyedTableTests extends AnyFunSuite with Matchers with TestsCommon with IntegrationPatience {
  val schema = Phones.schema ++ People.schema

  test("EntTableQuery#insert(KeylessEntity) does not insert twice [regression]") {
    val phone       = Phone("main", "1407124383")
    val countAction = Phones.countAction(phone)
    assert(db.run(countAction).futureValue == 0)
    assert(db.run(Phones.insert(phone) >> countAction).futureValue == 1)
  }

  test("Using lookup in a query") {
    val res = db run
      (for {
        id <- People.map(_.mapping) returning People.map(_.lookup) += Person("first1", "last1")
        _  <- Phones.map(_.mapping) += Phone("M", "111", Some(id))
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

  test("Using lookup in a predicate doesn't pollute the * projection") {
    // noinspection SimplifyBoolean
    val res = db run
      (for {
        _  <- People.map(_.mapping) += Person("first", "last")
        cs <- People.filter(people => people.lookup === EntityKey[Long, Person](1).asLookup || true).result.head
        k   = cs.key
      } yield k)
    assert(res.futureValue.isInstanceOf[Long])
  }
}
