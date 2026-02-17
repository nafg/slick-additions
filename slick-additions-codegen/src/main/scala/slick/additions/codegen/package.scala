package slick.additions

import scala.util.Try

import slick.jdbc.meta.MColumn


package object codegen {
  def snakeToCamel(s: String) = {
    def loop(cs: List[Char]): List[Char] =
      cs match {
        case '_' :: c :: rest => c.toUpper :: loop(rest)
        case c :: rest        => c :: loop(rest)
        case Nil              => Nil
      }

    loop(s.toList).mkString
  }

  class TryExtractor[A](f: String => A) {
    def unapply(string: String) = Try(f(string)).toOption
  }
  val AsBoolean = new TryExtractor(_.toBoolean)
  val AsInt    = new TryExtractor(_.toInt)
  val AsDouble = new TryExtractor(_.toDouble)

  object ColType {
    def unapply(col: MColumn) = Some((col.sqlType, col.typeName.toLowerCase, col.columnDef))
  }

  /** Extractor for matching a column by its table name and column name. Useful in `columnTypeOverride` and
    * `includeColumn` overrides for per-column matching.
    *
    * @example
    *   {{{
    * case ColName("hotline", "phone_number") => typ"PhoneNumber"
    *   }}}
    */
  object ColName {
    def unapply(col: MColumn): Some[(String, String)] = Some((col.table.name, col.name))
  }
}
