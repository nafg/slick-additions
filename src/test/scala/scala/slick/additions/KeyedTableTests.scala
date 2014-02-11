package scala.slick.additions

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.ShouldMatchers

import scala.slick.lifted.TableQuery

class KeyedTableTests extends FunSuite with ShouldMatchers with BeforeAndAfter {
  object driver extends scala.slick.driver.H2Driver with KeyedTableComponent
  import driver.simple._

  case class Phone(kind: String, number: String, person: People.Lookup = People.Lookup.NotSet)
  class Phones(tag: Tag) extends EntityTable[Long, Phone](tag, "phones") {
    def tableQuery = Phones
    def person = column[People.Lookup]("personid")
    def kind = column[String]("kind")
    def number = column[String]("number")
    def mapping = (kind, number, person) <-> (Phone.apply _, Phone.unapply _)
  }
  val Phones = TableQuery[Phones]

  case class Person(first: String, last: String, phones: People.OneToMany[Phones.type])
  object People extends EntityTable[Long, Person]("people") {
    def setPhoneLookup: Lookup => Phone => Phone = lu => _.copy(person = lu)
    def phonesLookup(k: Option[Long] = None, init: Seq[Phones.Ent] = null) =
      OneToMany[Phones.type](Phones, k map { x => Lookup(x) })(_.person, setPhoneLookup, init)

    override val lookupLenses = List(OneToManyLens(_.phones)(ps => _.copy(phones = ps)))

    def first = column[String]("first")
    def last = column[String]("last")

    def mapping = first ~ last <-> (
      k => Person(_, _, phonesLookup(k)),
      Person.unapply(_) map (t => (t._1, t._2))
    )

    def find(f: Query[(People.type, Phones.type), (People.KEnt, Phones.KEnt)] => Query[(People.type, Phones.type), (People.KEnt, Phones.KEnt)])(implicit session: Session) = {
      val q = f(Query(People) leftJoin Query(Phones) on (_.lookup is _.person))
      val list: List[(People.KEnt, Phones.KEnt)] = q.list
      val grouped = list.foldLeft(List.empty[(People.KEnt, List[Phones.KEnt])]){ case (xs, (person, phone)) =>
        val (matched, others) = xs partition (_._1.key == person.key)
        matched match {
          case Nil =>
            (person, phone :: Nil) :: others
          case y :: ys =>
            (y._1, phone :: y._2) :: ys ::: others
        }
      }
      grouped.collect {
        case (personEnt: SavedEntity[Long, Person], phones) =>
          personEnt.copy(value = personEnt.value.copy(phones = phonesLookup(Some(personEnt.key), phones)))
      }
    }
  }

  val db = Database.forURL("jdbc:h2:test", driver = "org.h2.Driver")

  val ddl = Phones.ddl ++ People.ddl

  before {
    db.withSession { implicit session: Session =>
      ddl.create
    }
  }

  after {
    db.withSession { implicit session: Session =>
      ddl.drop
    }
  }

  test("OneToMany") {
    db.withSession { implicit session: Session =>
      def testRoundTrip(in: People.Ent) = {
        val saved = People save in
        People.find(_ where (_._1.key is saved.key)) match {
          case loaded :: Nil =>
            println(s"Loaded $loaded")
            saved should equal (loaded)
            saved.value.phones().toSet should equal (loaded.value.phones().toSet)
            saved
          case x => fail(s"Found $x")
        }
      }

      val person = People.Ent(
        Person(
          "First",
          "Last",
          People.phonesLookup(
            None,
            List(
              Phones.Ent(Phone("home", "1234567890")),
              Phones.Ent(Phone("cell", "0987654321"))
            )
          )
        )
      )
      val saved = withClue("Original Person: ") { testRoundTrip(person) }

      // Delete one phone, modify the other, and add another
      val modifiedPhones = saved.map { p =>
        p.copy(
          phones = p.phones.map(
            phones =>
            phones.collect {
              case e if e.value.kind == "cell" =>
                e.map(_.copy(kind = "mobile"))
            } :+ Phones.Ent(Phone("work", "5555555555"))
          )
        )
      }
      println("modifiedPhones: " + modifiedPhones)
      withClue("Modified Person: ") { testRoundTrip(modifiedPhones) }
    }
  }
}
