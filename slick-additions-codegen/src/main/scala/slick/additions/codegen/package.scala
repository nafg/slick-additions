package slick.additions

import scala.util.Try


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

  /** Extractor that destructures a [[GenerationRules.ColumnMetadata]] into `(sqlType, typeNameLower, columnDef)`.
    * Useful in [[GenerationRules.baseColumnDefault]] overrides for matching by type name and default value.
    */
  object ColType {
    def unapply(col: GenerationRules.ColumnMetadata) = Some((col.jdbcType, col.typeNameLower, col.default))
  }

  /** Extractor for matching a column by its table name and column name. Useful in [[GenerationRules.baseColumnType]]
    * and [[GenerationRules.includeColumn]] overrides for per-column matching.
    *
    * @example
    *   {{{
    * override def baseColumnType =
    *   super.baseColumnType.asFallbackFor {
    *     case ColName("hotline", "phone_number") => typ"PhoneNumber"
    *   }
    *   }}}
    */
  object ColName {
    def unapply(col: GenerationRules.ColumnMetadata): Some[(String, String)] = Some((col.table.name, col.name))
  }
}
