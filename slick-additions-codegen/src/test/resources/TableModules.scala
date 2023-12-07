package com.acme.tablemodules
import slick.additions.AdditionsProfile
import slick.lifted.MappedProjection
trait SlickProfile extends slick.jdbc.H2Profile with AdditionsProfile {
  object myApi extends JdbcAPI with AdditionsApi
  override val api: myApi.type = myApi
}
object SlickProfile extends SlickProfile
import SlickProfile.api._
import slick.additions.entity.Lookup
object TableModules {
  object Colors extends EntityTableModule[Long, ColorsRow]("colors") {
    class Row(tag: Tag) extends BaseEntRow(tag) {
      override def keyColumnName = "id"
      val name                   = column[String]("name")
      def mapping: MappedProjection[ColorsRow] = name.<>(
        {
          ColorsRow.apply
        },
        ColorsRow.unapply
      )
    }
  }
  object People extends EntityTableModule[Long, PeopleRow]("people") {
    class Row(tag: Tag) extends BaseEntRow(tag) {
      override def keyColumnName = "id"
      val first                  = column[String]("first")
      val last                   = column[String]("last")
      val city                   = column[String]("city")
      val dateJoined             = column[java.time.LocalDate]("date_joined")
      val balance                = column[BigDecimal]("balance")
      val bestFriend = column[Option[Lookup[Long, PeopleRow]]]("best_friend")
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
      def mapping: MappedProjection[PeopleRow] = (
        (
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
          col23
        ),
        col24
      ).<>(
        {
          {
            case (
                  (
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
                    col23
                  ),
                  col24
                ) =>
              PeopleRow(
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
          }
        },
        (rec: PeopleRow) =>
          Some(
            (
              (
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
                rec.col22,
                rec.col23
              ),
              rec.col24
            )
          )
      )
    }
  }
}
