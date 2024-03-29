package plain
case class ColorsRow(id: Long, name: String)
case class PeopleRow(
    id: Long,
    first: String,
    last: String,
    city: String = "New York",
    dateJoined: java.time.LocalDate = java.time.LocalDate.now(),
    balance: BigDecimal = BigDecimal("0.0"),
    bestFriend: Option[Long] = None,
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
