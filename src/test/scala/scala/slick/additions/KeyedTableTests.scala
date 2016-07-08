package scala.slick.additions

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.Matchers

class KeyedTableTests extends FunSuite with Matchers with BeforeAndAfter {
  object driver extends scala.slick.driver.H2Driver with KeyedTableComponent
  import driver.simple._

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

    def findPeople(f: Query[(People, Phones), (People#KEnt, Phones#KEnt), Seq] => Query[(People, Phones), (People#KEnt, Phones#KEnt), Seq])(implicit session: Session) = {
      val q = f(People leftJoin Phones on (People.lookup(_) === _.person))
      val list: List[(People#KEnt, Phones#KEnt)] = q.list
      val grouped = list.foldLeft(List.empty[(People#KEnt, List[Phones#KEnt])]) {
        case (xs, (person, phone)) =>
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

  val db = Database.forURL("jdbc:h2:./test", driver = "org.h2.Driver")

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
      def testRoundTrip(in: Ent[People]) = {
        val saved = People save in
        People.findPeople(_ filter (_._1.key === saved.key)) match {
          case loaded :: Nil =>
            println(s"Loaded $loaded")
            saved should equal (loaded)
            saved.value.phones.items.toSet should equal (loaded.value.phones.items.toSet)
            saved
          case x => fail(s"Found $x")
        }
      }

      val person = driver.simple.Ent[People](
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
      val modifiedPhones = saved.map { p =>
        p.copy(
          phones = p.phones.map(
            phones =>
            phones.collect {
              case e if e.value.kind == "cell" =>
                e.map(_.copy(kind = "mobile"))
            } :+ Ent[Phones](Phone("work", "5555555555"))
          )
        )
      }
      println("modifiedPhones: " + modifiedPhones)
      withClue("Modified Person: ") { testRoundTrip(modifiedPhones) }
    }
  }

  test("Using lookup in a query") {
    db.withSession { implicit session: Session =>
      val id = People.map(_.mapping) returning People.map(_.key) insert Person("first1", "last1", People.phonesLookup())
      Phones.map(_.mapping) insert Phone("M", "111", People.Lookup(id))
      People.filter(People.lookup(_) in Phones.map(_.person)).list
    }
  }

  test("Lookup.NotSet.fetched does not throw an exception") {
    db.withSession { implicit session: Session =>
      val phone = Phone("M", "222")
      phone.person() shouldBe None
      phone.person.fetched shouldBe People.Lookup.NotSet
    }
  }
}
