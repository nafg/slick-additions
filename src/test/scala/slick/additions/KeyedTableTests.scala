package slick.additions

import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.Matchers

class KeyedTableTests extends FunSuite with Matchers with BeforeAndAfter {
  object driver extends scala.slick.driver.H2Driver with KeyedTableComponent
  import driver.api._

  case class Phone(kind: String, number: String, person: People.Lookup = People.Lookup.NotSet)
  class Phones(tag: Tag) extends EntityTable[Long, Phone](tag, "phones") {
    def tableQuery = Phones
    def person = column[People.Lookup]("personid")
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
      k => (t: (String, String)) => {
        println("In mapping, k = " + k)
        Person(t._1, t._2, People.phonesLookup(k))
      },
      (p: Person) => Person.unapply(p) map (t => (t._1, t._2))
    )
  }

  object People extends EntTableQuery[Long, Person, People](new People(_)) {
    def setPhoneLookup: People.Lookup => Phone => Phone = lu => _.copy(person = lu)
    def phonesLookup(k: Option[Long] = None, init: Seq[Phones#Ent] = null) =
      People.OneToMany(Phones, k map { x => People.Lookup(x) })(_.person, setPhoneLookup, init)

    override val lookupLenses = List(OneToManyLens[Long, Phone, Phones](_.phones)(ps => _.copy(phones = ps)))

    def findPeople(f: Query[(People, Rep[Option[Phones]]), (People#KEnt, Option[Phones#KEnt]), Seq] => Query[(People, Rep[Option[Phones]]), (People#KEnt, Option[Phones#KEnt]), Seq])(implicit ec: ExecutionContext) = {
      val q = f(People joinLeft Phones on (People.lookup(_) === _.person))
      db run q.result map { list =>
        val grouped = list.foldLeft(List.empty[(People#KEnt, List[Phones#KEnt])]) {
          case (xs, (person, phone)) =>
            val (matched, others) = xs partition (_._1.key == person.key)
            matched match {
              case Nil =>
                (person, phone.toList) :: others
              case y :: ys =>
                (y._1, phone.toList ::: y._2) :: ys ::: others
            }
        }
        grouped.collect {
          case (personEnt: SavedEntity[Long, Person], phones) =>
            personEnt.copy(value = personEnt.value.copy(phones = phonesLookup(Some(personEnt.key), phones)))
        }
      }
    }
  }

  val db = Database.forURL("jdbc:h2:test", driver = "org.h2.Driver")

  val schema = Phones.schema ++ People.schema

  before {
    db run schema.create
  }

  after {
    db run schema.drop
  }

  test("OneToMany") {
    import scala.concurrent.ExecutionContext.Implicits.global
    def testRoundTrip(in: Ent[People]) = {
      db.run(People save in) flatMap { saved =>
        People.findPeople(_ filter (_._1.key === saved.key)) map {
          case loaded :: Nil =>
            println(s"Loaded $loaded")
            saved should equal (loaded)
            saved.value.phones.items.toSet should equal (loaded.value.phones.items.toSet)
            saved
          case x => fail(s"Found $x")
        }
      }
    }

    val person = driver.api.Ent[People](
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
    val saved = withClue("Original Person: ") { testRoundTrip(person) }

    // Delete one phone, modify the other, and add another
    val modifiedPhones = saved.map { p: SavedEntity[Long, Person] =>
      p.copy(
        // phones = p.phones.map(
        //   phones =>
        //   phones.collect {
        //     case e if e.value.kind == "cell" =>
        //       e.map(_.copy(kind = "mobile"))
        //   } :+ Ent[Phones](Phone("work", "5555555555"))
        // )
      )
    }
    println("modifiedPhones: " + modifiedPhones)
    withClue("Modified Person: ") { modifiedPhones foreach testRoundTrip }
  }

  test("Using lookup in a query") {
    import scala.concurrent.ExecutionContext.Implicits.global
    db run (for {
      id <- People.map(_.mapping) returning People.map(_.key) += Person("first1", "last1", People.phonesLookup())
      _  <- Phones.map(_.mapping) += Phone("M", "111", People.Lookup(id))
      xs  <- People.filter(People.lookup(_) in Phones.map(_.person)).result
    } yield xs)
  }

  test("Lookup.NotSet.fetched does not throw an exception") {
    import scala.concurrent.ExecutionContext.Implicits.global
    val phone = Phone("M", "222")
    Await.result(db.run(phone.person()), Duration.Inf) shouldBe None
    Await.result(db.run(phone.person.fetched), Duration.Inf) shouldBe People.Lookup.NotSet
  }
}
