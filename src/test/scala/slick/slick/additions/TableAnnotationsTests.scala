package slick.additions

import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class TableAnnotationsTests extends FunSuite with Matchers with BeforeAndAfter {
  object driver extends scala.slick.driver.H2Driver with KeyedTableComponent
  import driver.api._

  case class Person(first: String, last: String, age: Int, id: Option[Long] = None)

  test("ExpandTable on empty object") {
    @ExpandTable(classOf[Person]) object People

    def ps: DBIO[Seq[Person]] = People.result
    def ages: DBIO[Seq[Int]] = People.map((_: People).age).result
  }

  test("ExpandTable on empty class") {
    pending
    //    @ExpandTable(classOf[Person]) class People(t: Tag)

//    def ps: List[Person] = People.list
//    def ages: List[Int] = People.map(_.age).list
  }
}
