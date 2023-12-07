package entity
import slick.additions.entity.Lookup, io.circe.generic.JsonCodec,
  monocle.macros.Lenses
@JsonCodec @Lenses case class ColorsRow(name: String)
object ColorsRow
@JsonCodec @Lenses case class PeopleRow(
    first: String,
    last: String,
    city: String = "New York",
    dateJoined: java.time.LocalDate = java.time.LocalDate.now(),
    balance: BigDecimal = BigDecimal("0.0"),
    bestFriend: Option[Lookup[Long, PeopleRow]] = None,
    col8: Option[Double] = None,
    col9: Option[Boolean] = None,
    col10: Option[Int] = None,
    col11: Option[Int] = None,
    col12: Option[Int] = None,
    col13: Option[Int] = None,
    col14: Option[Int] = None,
    col15: Option[Int] = None,
    col16: Option[Int] = None,
    col17: Option[Int] = None,
    col18: Option[Int] = None,
    col19: Option[Int] = None,
    col20: Option[Int] = None,
    col21: Option[Int] = None,
    col22: Option[Int] = None,
    col23: Option[Int] = None,
    col24: Option[Int] = None
)
object PeopleRow
