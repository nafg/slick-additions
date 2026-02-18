package slick.additions.codegen

import java.sql.Types

import scala.concurrent.ExecutionContext
import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.*

import org.slf4j.LoggerFactory


/** Generates object configs (e.g. [[TableConfig]]s) and their [[ColumnConfig]]s by reading database metadata. Extend
  * this directly or indirectly, and override methods freely to customize.
  *
  * Subclasses define `ObjectConfigType` to determine what kind of config is produced:
  *   - [[BasicGenerationRules]] produces [[TableConfig]]
  *   - [[EntityGenerationRules]] produces [[EntityGenerationRules.ObjectConfig]] (a sealed trait)
  *
  * The default implementation uses camelCase for corresponding snake_case names in the database, and names model classes
  * by appending `Row` to the camel-cased table name.
  */
trait GenerationRules  {
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
    * @param currentTableMetadata
    *   the table this column belongs to
    */
  // noinspection ScalaWeakerAccess,ScalaUnusedSymbol
  protected def includeColumn(column: MColumn, currentTableMetadata: GenerationRules.TableMetadata): Boolean = true

  // noinspection ScalaWeakerAccess
  protected def namingRules: NamingRules = NamingRules.ModelSuffixedWithRow

  /** Determine the base Scala type for a column. If the column is nullable, the type returned from this method will be
    * wrapped in `Option[...]`.
    *
    * Extend by overriding with `orElse`.
    *
    * @example
    *   {{{ override def baseColumnType(current: TableMetadata, all: Seq[TableMetadata]) = super.baseColumnType(current,
    *   all).orElse { case ... } }}}
    * @see
    *   [[columnConfig]]
    */
  protected def baseColumnType(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  )
    : PartialFunction[MColumn, Type] = {
    case ColType(Types.NUMERIC, "numeric", _)                                => typ"BigDecimal"
    case ColType(Types.DOUBLE, "double precision" | "float8", _)             => typ"Double"
    case ColType(Types.BIGINT, "bigserial" | "bigint" | "int8", _)           => typ"Long"
    case ColType(Types.BIT | Types.BOOLEAN, "bool" | "boolean", _)           => typ"Boolean"
    case ColType(Types.INTEGER, _, _)                                        => typ"Int"
    case ColType(Types.VARCHAR, "character varying" | "text" | "varchar", _) => typ"String"
    case ColType(Types.DATE, "date", _)                                      =>
      term"java".termSelect(term"time").typeSelect(typ"LocalDate")
    case ColType(_, "lo", _)                                                 =>
      term"java".termSelect(term"sql").typeSelect(typ"Blob")
  }

  /** Determine the base Scala default value for a column. If the column is nullable, the expression returned from this
    * method will be wrapped in `Some(...)`.
    *
    * Extend by overriding with `orElse`.
    *
    * @example
    *   {{{ override def baseColumnDefault(current: TableMetadata, all: Seq[TableMetadata]) =
    *   super.baseColumnDefault(current, all).orElse { case ... } }}}
    * @see
    *   [[columnConfig]]
    */
  // noinspection ScalaWeakerAccess,ScalaUnusedSymbol
  protected def baseColumnDefault(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  )
    : PartialFunction[MColumn, Term] = {
    case ColType(Types.BIT, "boolean" | "bool", Some(AsBoolean(b)))              => Lit.Boolean(b)
    case ColType(Types.INTEGER, _, Some(AsInt(i)))                               => Lit.Int(i)
    case ColType(Types.DOUBLE, "double precision" | "float8", Some(AsDouble(d))) => Lit.Double(d)
    case ColType(Types.NUMERIC, "numeric", Some(s))                              =>
      term"BigDecimal".termApply(Lit.String(s))
    case ColType(Types.DATE, "date", Some("now()" | "LOCALTIMESTAMP"))           =>
      term"java".termSelect("time").termSelect("LocalDate").termSelect("now")
        .termApply()
    case ColType(
          Types.VARCHAR,
          "character varying" | "text" | "varchar",
          Some(s)
        ) =>
      Lit.String(s.stripPrefix("'").stripSuffix("'"))
  }

  protected def baseColumnType(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata],
    column: MColumn
  ): Type =
    baseColumnType(currentTableMetadata, all).applyOrElse(
      column,
      (_: MColumn) => {
        logger.warn(
          s"Column type not matched by `baseColumnType` for column: ${currentTableMetadata.table.name} ${column.name}"
        )
        typ"Nothing"
      }
    )

  // noinspection ScalaWeakerAccess
  protected def columnConfig(
    column: MColumn,
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  )
    : ColumnConfig = {
    val ident    = Term.Name(namingRules.columnNameToIdentifier(column.name))
    val typ0     = baseColumnType(currentTableMetadata, all, column)
    val default0 = baseColumnDefault(currentTableMetadata, all).lift(column)

    val (typ, default) =
      if (!column.nullable.contains(true))
        typ0                        -> default0
      else
        typ"Option".typeApply(typ0) ->
          Some(default0.map(t => term"Some".termApply(t)).getOrElse(term"None"))

    ColumnConfig(
      column = column,
      tableFieldTerm = ident,
      modelFieldTerm = ident,
      scalaType = typ,
      scalaDefault = default
    )
  }

  protected def columnConfigs(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  ) =
    currentTableMetadata.columns.toList
      .filter(includeColumn(_, currentTableMetadata))
      .map(columnConfig(_, currentTableMetadata, all))

  protected def objectConfig(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  )
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
    } yield infos.map(objectConfig(_, infos))
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
}
