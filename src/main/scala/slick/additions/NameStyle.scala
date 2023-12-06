package slick.additions

class NameStyle(val identToDb: String => String) {
  def columnName(name: String): String                = identToDb(name)
  def foreignKeyName(tableName: String, name: String) = tableName + "__" + identToDb(name)
  def indexName(tableName: String, name: String)      = tableName + "__" + identToDb(name)
}

object NameStyle {
  private def snakify(s: String) =
    s.toList match {
      case Nil           => ""
      case char :: chars =>
        chars
          .foldLeft(char :: Nil) {
            case (x :: xs, c) if x.isLower && c.isUpper =>
              c.toLower :: '_' :: x :: xs
            case (xs, c)                                =>
              c :: xs
          }
          .reverse
          .mkString
    }
  val Exact                      = new NameStyle(identity)
  val Snakify                    = new NameStyle(snakify)
}
