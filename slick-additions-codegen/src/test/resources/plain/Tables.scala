package plain
import slick.jdbc.H2Profile.api._
object Tables {
  class Colors(_tableTag: Tag)
      extends Table[ColorsRow](_tableTag, Some("PUBLIC"), "colors") {
    val id                                      = column[Long]("id")
    val name                                    = column[String]("name")
    def * : slick.lifted.ProvenShape[ColorsRow] = (id, name).mapTo[ColorsRow]
  }
  lazy val Colors = TableQuery[Colors]
  class People(_tableTag: Tag)
      extends Table[PeopleRow](_tableTag, Some("PUBLIC"), "people") {
    val id         = column[Long]("id")
    val first      = column[String]("first")
    val last       = column[String]("last")
    val city       = column[String]("city")
    val dateJoined = column[java.time.LocalDate]("date_joined")
    val balance    = column[BigDecimal]("balance")
    val bestFriend = column[Option[Long]]("best_friend")
    val col8       = column[Option[Double]]("col8")
    val col9       = column[Option[Boolean]]("col9")
    val col10      = column[Option[Int]]("col10")
    val col11      = column[Option[Int]]("col11")
    val col12      = column[Option[Int]]("col12")
    val col13      = column[Option[Int]]("col13")
    val col14      = column[Option[Int]]("col14")
    val col15      = column[Option[Int]]("col15")
    val col16      = column[Option[Int]]("col16")
    val col17      = column[Option[Int]]("col17")
    val col18      = column[Option[Int]]("col18")
    val col19      = column[Option[Int]]("col19")
    val col20      = column[Option[Int]]("col20")
    val col21      = column[Option[Int]]("col21")
    val col22      = column[Option[Int]]("col22")
    val col23      = column[Option[Int]]("col23")
    val col24      = column[Option[Int]]("col24")
    def * : slick.lifted.ProvenShape[PeopleRow] = (
      (
        id,
        first,
        last,
        city,
        dateJoined,
        balance,
        bestFriend,
        col8,
        col9,
        col10,
        col11,
        col12,
        col13,
        col14,
        col15,
        col16,
        col17,
        col18,
        col19,
        col20,
        col21,
        col22
      ),
      col23,
      col24
    ).<>(
      {
        case (
              (
                id,
                first,
                last,
                city,
                dateJoined,
                balance,
                bestFriend,
                col8,
                col9,
                col10,
                col11,
                col12,
                col13,
                col14,
                col15,
                col16,
                col17,
                col18,
                col19,
                col20,
                col21,
                col22
              ),
              col23,
              col24
            ) =>
          PeopleRow(
            id,
            first,
            last,
            city,
            dateJoined,
            balance,
            bestFriend,
            col8,
            col9,
            col10,
            col11,
            col12,
            col13,
            col14,
            col15,
            col16,
            col17,
            col18,
            col19,
            col20,
            col21,
            col22,
            col23,
            col24
          )
      },
      (rec: PeopleRow) =>
        Some(
          (
            (
              rec.id,
              rec.first,
              rec.last,
              rec.city,
              rec.dateJoined,
              rec.balance,
              rec.bestFriend,
              rec.col8,
              rec.col9,
              rec.col10,
              rec.col11,
              rec.col12,
              rec.col13,
              rec.col14,
              rec.col15,
              rec.col16,
              rec.col17,
              rec.col18,
              rec.col19,
              rec.col20,
              rec.col21,
              rec.col22
            ),
            rec.col23,
            rec.col24
          )
        )
    )
  }
  lazy val People = TableQuery[People]
}
