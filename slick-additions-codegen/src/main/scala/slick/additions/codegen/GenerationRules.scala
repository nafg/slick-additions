package slick.additions.codegen

import scala.concurrent.ExecutionContext
import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.*

import org.slf4j.LoggerFactory


/** Provides the [[PartialFunction_asFallbackFor.asFallbackFor]] extension method on `PartialFunction`, which reverses
  * the `orElse` composition order. This lets you write `super.baseColumnType.asFallbackFor { case ... => ... }` so that
  * the new cases take priority without type inference issues from bare partial function literals.
  */
trait PartialFunctionUtils {
  implicit class PartialFunction_asFallbackFor[A, B](self: PartialFunction[A, B]) {

    /** Returns a `PartialFunction` that tries `other` first, falling back to `self`. Equivalent to
      * `other.orElse(self)`, but avoids the Scala 2 type inference limitation where a bare `{ case ... }.orElse(x)`
      * cannot infer the partial function's input type.
      *
      * @example
      *   {{{
      * override def baseColumnType =
      *   super.baseColumnType.asFallbackFor {
      *     case ColName("my_table", "my_col") => typ"MyType"
      *   }
      *   }}}
      */
    def asFallbackFor(other: PartialFunction[A, B]): PartialFunction[A, B] = other.orElse(self)
  }
}

/** Generates object configs (e.g. [[TableConfig]]s) and their [[ColumnConfig]]s by reading database metadata. Extend
  * this directly or indirectly, and override methods freely to customize.
  *
  * Subclasses define `ObjectConfigType` to determine what kind of config is produced:
  *   - [[BasicGenerationRules]] produces [[TableConfig]]
  *   - [[EntityGenerationRules]] produces [[EntityGenerationRules.ObjectConfig]] (a sealed trait)
  *
  * The default implementation uses camelCase for corresponding snake_case names in the database and names model classes
  * by appending `Row` to the camel-cased table name.
  */
trait GenerationRules extends PartialFunctionUtils {
  type ObjectConfigType

  private val logger = LoggerFactory.getLogger(getClass)

  def extraImports = List.empty[String]

  // noinspection ScalaWeakerAccess,ScalaUnusedSymbol
  protected def includeTable(table: MTable): Boolean = true

  /** Whether to include a column in the generated code. Override to exclude infrastructure columns (e.g. tenant IDs,
    * audit timestamps).
    *
    * @param column
    *   the column metadata
    */
  // noinspection ScalaWeakerAccess,ScalaUnusedSymbol
  protected def includeColumn(column: GenerationRules.ColumnMetadata): Boolean = true

  // noinspection ScalaWeakerAccess
  protected def namingRules: NamingRules = NamingRules.ModelSuffixedWithRow

  /** Maps lowercase SQL type names to Scala types. This is a simpler hook than [[baseColumnType]] for when you only
    * need to match on the type name string, without access to the full [[GenerationRules.ColumnMetadata]].
    *
    * Extend with `orElse` to add new type mappings as fallbacks, or with
    * [[PartialFunction_asFallbackFor.asFallbackFor]] to override existing ones.
    *
    * @see
    *   [[baseColumnType]], [[PostgresGenerationRules]]
    */
  protected def baseColumnTypeMapping: PartialFunction[String, Type] = {
    case "numeric"                       => typ"BigDecimal"
    case "double precision"              => typ"Double"
    case "bigint"                        => typ"Long"
    case "boolean"                       => typ"Boolean"
    case "integer"                       => typ"Int"
    case "character varying" | "varchar" => typ"String"
    case "date"                          => term"java".termSelect(term"time").typeSelect(typ"LocalDate")
  }

  /** Determine the base Scala type for a column. If the column is nullable, the type returned from this method will be
    * wrapped in `Option[...]`. After this, [[transformColumnType]] is called to allow further wrapping (e.g., for array
    * columns).
    *
    * The default implementation delegates to [[baseColumnTypeMapping]] via the column's lowercase type name.
    *
    * Extend by overriding with `orElse` or [[PartialFunction_asFallbackFor.asFallbackFor]].
    *
    * @example
    *   {{{
    * override def baseColumnType =
    *   super.baseColumnType.asFallbackFor {
    *     case ColName("my_table", "my_col") => typ"MyType"
    *   }
    *   }}}
    * @see
    *   [[columnConfig]], [[baseColumnTypeMapping]]
    */
  protected def baseColumnType: PartialFunction[GenerationRules.ColumnMetadata, Type] =
    Function.unlift(col => baseColumnTypeMapping.lift(col.typeNameLower))

  /** Determine the base Scala default value for a column. If the column is nullable, the expression returned from this
    * method will be wrapped in `Some(...)`.
    *
    * Extend with `orElse` to add new default-value cases, or with [[PartialFunction_asFallbackFor.asFallbackFor]] to
    * override existing ones.
    *
    * @example
    *   {{{
    * override def baseColumnDefault =
    *   super.baseColumnDefault.orElse {
    *     case ColType(_, "my_type", Some(s)) => Lit.String(s)
    *   }
    *   }}}
    * @see
    *   [[columnConfig]]
    */
  // noinspection ScalaWeakerAccess,ScalaUnusedSymbol
  protected def baseColumnDefault: PartialFunction[GenerationRules.ColumnMetadata, Term] = {
    case ColType(_, "boolean", Some(AsBoolean(b)))            => Lit.Boolean(b)
    case ColType(_, "integer", Some(AsInt(i)))                => Lit.Int(i)
    case ColType(_, "bigint", Some(AsLong(l)))                => Lit.Long(l)
    case ColType(_, "double precision", Some(AsDouble(d)))    => Lit.Double(d)
    case ColType(_, "character varying" | "varchar", Some(s)) => Lit.String(s.stripPrefix("'").stripSuffix("'"))
    case ColType(_, "numeric", Some(s))                       => term"BigDecimal".termApply(Lit.String(s))
    case ColType(_, "date", Some("now()" | "LOCALTIMESTAMP")) =>
      term"java".termSelect("time").termSelect("LocalDate").termSelect("now").termApply()
  }

  /** Hook to transform the resolved base type before nullable/Option wrapping. Override to customize the Scala type for
    * specific columns (e.g., wrapping array columns in a collection type). The default implementation returns the base
    * type unchanged.
    *
    * @param column
    *   the column metadata
    * @param baseType
    *   the Scala type resolved by [[baseColumnType]]
    * @return
    *   the (possibly transformed) type
    */
  // noinspection ScalaWeakerAccess
  protected def transformColumnType(column: GenerationRules.ColumnMetadata, baseType: Type): Type = baseType

  // noinspection ScalaWeakerAccess
  protected def columnConfig(columnMetadata: GenerationRules.ColumnMetadata): ColumnConfig = {
    val ident = Term.Name(namingRules.columnNameToIdentifier(columnMetadata.name))

    val baseDefault = baseColumnDefault.lift(columnMetadata)
    val baseType    =
      transformColumnType(
        column = columnMetadata,
        baseType =
          baseColumnType.applyOrElse(
            columnMetadata,
            { (_: GenerationRules.ColumnMetadata) =>
              logger.warn(
                s"Column type not matched by `baseColumnType` for column: ${columnMetadata.table.name}.${columnMetadata.name}"
              )
              typ"Nothing"
            }
          )
      )

    val (fullType, fullDefault) =
      if (!columnMetadata.nullable)
        baseType                        -> baseDefault
      else
        typ"Option".typeApply(baseType) -> Some(baseDefault.map(t => term"Some".termApply(t)).getOrElse(term"None"))

    ColumnConfig(
      column = columnMetadata.column,
      tableFieldTerm = ident,
      modelFieldTerm = ident,
      scalaType = fullType,
      scalaDefault = fullDefault
    )
  }

  protected def columnConfigs(tableMetadata: GenerationRules.TableMetadata) =
    tableMetadata.columns.toList.flatMap { column =>
      val columnMetadata =
        GenerationRules.ColumnMetadata(
          column = column,
          primaryKey = tableMetadata.primaryKeys.find(_.column == column.name),
          foreignKey = tableMetadata.foreignKeys.find(_.fkColumn == column.name)
        )
      if (includeColumn(columnMetadata))
        Some(columnConfig(columnMetadata))
      else
        None
    }

  protected def objectConfig(tableMetadata: GenerationRules.TableMetadata)
    : ObjectConfigType

  def objectConfigs(slickProfileClass: Class[? <: JdbcProfile])(implicit ec: ExecutionContext)
    : DBIO[List[ObjectConfigType]] = {
    val slickProfileInstance = slickProfileClass.getField("MODULE$").get(null).asInstanceOf[JdbcProfile]
    for {
      tables        <- slickProfileInstance.defaultTables
      includedTables = tables.toList.filter(includeTable)
      infos         <- DBIO.sequence(
                         includedTables.map { t =>
                           for {
                             cols <- t.getColumns
                             pks  <- t.getPrimaryKeys
                             fks  <- t.getImportedKeys
                           } yield GenerationRules.TableMetadata(
                             table = t,
                             columns = cols,
                             primaryKeys = pks,
                             foreignKeys = fks
                           )
                         }
                       )
    } yield infos.map(objectConfig)
  }
}
object GenerationRules {

  /** Information about a table obtained from the Slick JDBC metadata APIs
    */
  case class TableMetadata(
    table: MTable,
    columns: Seq[MColumn],
    primaryKeys: Seq[MPrimaryKey],
    foreignKeys: Seq[MForeignKey])

  /** Metadata about a single column, bundling the JDBC column info with its primary key and foreign key associations.
    * This is the input type for [[GenerationRules.baseColumnType]], [[GenerationRules.baseColumnDefault]], and the
    * extractors in the `codegen` package object ([[ColType]], [[ColName]]).
    */
  case class ColumnMetadata(column: MColumn, primaryKey: Option[MPrimaryKey], foreignKey: Option[MForeignKey]) {
    def table         = column.table
    def name          = column.name
    def typeNameLower = column.typeName.toLowerCase
    def jdbcType      = column.sqlType
    def default       = column.columnDef
    def nullable      = column.nullable.contains(true)
  }
}
