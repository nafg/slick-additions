package scala.slick.test

object LookupBugTests extends App {
  object driver extends scala.slick.driver.H2Driver
  import driver.simple._
  val db = Database.forURL("jdbc:h2:test0", driver = "org.h2.Driver")

  implicit def mapper: BaseColumnType[Unfetched] = MappedColumnType.base[Unfetched, Long](_.key, Unfetched(_))
  case class Unfetched(key: Long)

  case class Phone(person: Unfetched)
  class Phones(tag: Tag) extends Table[Phone](tag, "phones") {
    def person = column[Unfetched]("personid")
    def * = person <> (Phone.apply, Phone.unapply _)
  }
  val Phones = TableQuery[Phones]
  case class Person(name: String)
  class People(tag: Tag) extends Table[(Long, Person)](tag, "people") {
    def key = column[Long]("id", O.AutoInc)
    def name = column[String]("name")
    def * = (key, name).shaped <> ({ case (k, v) => (k, Person(v)) }, (ke: (Long, Person)) => Some((ke._1, ke._2.name)))
  }
  val People = TableQuery[People]

  val ddl = Phones.ddl ++ People.ddl

  db.withSession { implicit session: Session =>
    ddl.create
    try {
      val id = People.map(_.name) returning People.map(_.key) insert "a"
      Phones.map(_.person) insert Unfetched(id)
      People.filter(_.column[Unfetched]("id") in Phones.map(_.person)).list
    } finally ddl.drop
  }
}
