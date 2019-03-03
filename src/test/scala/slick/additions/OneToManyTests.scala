package slick.additions

import scala.concurrent.ExecutionContext.Implicits.global

import slick.additions.entity.SavedEntity
import slick.additions.test.TestsCommon
import slick.additions.test.driver.api._

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSuite, Matchers}


class OneToManyTests extends FunSuite with Matchers with TestsCommon with ScalaFutures with IntegrationPatience {
  case class Phone(kind: String, number: String, person: Option[People.Lookup] = None)
  class Phones(tag: Tag) extends EntityTable[Long, Phone](tag, "phones") {
    def tableQuery = Phones
    def person = column[Option[People.Lookup]]("person_id")
    def kind = column[String]("kind")
    def number = column[String]("number")
    def mapping = (kind, number, person) <-> (_ => Phone.tupled, Phone.unapply)
  }
  object Phones extends EntTableQuery[Long, Phone, Phones](new Phones(_))

  case class Person(first: String, last: String, phones: People.OneToMany[Long, Phone, Phones])
  class People(tag: Tag) extends EntityTable[Long, Person](tag, "people") {
    def tableQuery = People

    def first = column[String]("first")
    def last = column[String]("last")

    def mapping = (first, last) <-> (
      k => (t: (String, String)) => Person(t._1, t._2, People.phonesLookup(k)),
      (p: Person) => Person.unapply(p) map (t => (t._1, t._2))
    )
  }

  object People extends EntTableQuery[Long, Person, People](new People(_)) {
    def setPhoneLookup: Option[People.Lookup] => Phone => Phone = lu => _.copy(person = lu)
    def phonesLookup(k: Option[Long] = None, init: Seq[Phones#Ent] = null) =
      People.OneToMany(Phones, k.map(People.Lookup(_)))(_.person, setPhoneLookup, init)

    override val lookupLenses = List(OneToManyLens[Long, Phone, Phones](_.phones)(ps => _.copy(phones = ps)))
  }

  val schema = Phones.schema ++ People.schema


  test("OneToMany") {
    def saveAndLoadTest(in: Ent[People]) = {
      def reloadPhones(person: SavedEntity[Long, Person]) = {
        Phones.filter(_.person === People.Lookup(person)).result.map { phones =>
          SavedEntity(
            person.key,
            person.value.copy(phones = person.value.phones.copy(phones.sortBy(_.key), isFetched = true))
          )
        }
      }
      val saved = db.run(People.save(in)).futureValue
      val loaded = db.run(reloadPhones(saved)).futureValue
      saved should equal(loaded)
      saved.value.phones.items.toSet should equal(loaded.value.phones.items.toSet)
      saved
    }

    val person = Ent[People](
      Person(
        "First",
        "Last",
        People.phonesLookup(
          None,
          List(
            Ent[Phones](Phone("home", "1234567890")),
            Ent[Phones](Phone("cell", "0987654321"))
          )
        )
      )
    )

    val saved = withClue("Original Person: ") {
      saveAndLoadTest(person)
    }

    // Delete one phone, modify the other, and add another
    val modifiedPhones = saved.map { p =>
      p.copy(
        phones = p.phones.map(phones =>
          phones.collect {
            case phone if phone.value.kind == "cell" =>
              phone.map(_.copy(kind = "mobile"))
          } :+ Ent[Phones](Phone("work", "5555555555"))
        )
      )
    }

    withClue("Modified Person: ") {
      saveAndLoadTest(modifiedPhones)
    }
  }
}
