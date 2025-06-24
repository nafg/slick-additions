package slick.additions.codegen

import java.nio.file.Path
import java.sql.Types

import scala.concurrent.ExecutionContext
import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.*

import org.slf4j.LoggerFactory


/** Information about a table obtained from the Slick JDBC metadata APIs
  */
case class TableMetadata(
  table: MTable,
  columns: Seq[MColumn],
  primaryKeys: Seq[MPrimaryKey],
  foreignKeys: Seq[MForeignKey])

/** How a database column is to be represented in code
  *
  * @param column
  *   the column this is for
  * @param tableFieldTerm
  *   the identifier used in the Slick table definition
  * @param modelFieldTerm
  *   the identifier used in the model class
  * @param scalaType
  *   the type that will represent data in the column in code
  * @param scalaDefault
  *   the default value to provide in the model class
  */
case class ColumnConfig(
  column: MColumn,
  tableFieldTerm: Term.Name,
  modelFieldTerm: Term.Name,
  scalaType: Type,
  scalaDefault: Option[Term])

/** How a database table is to be represented in code
  *
  * @param tableMetadata
  *   the metadata for the table this is for
  * @param tableClassName
  *   the name of the Slick table definition
  * @param modelClassName
  *   the name of the model class
  * @param columns
  *   configurations for this table's columns
  */
case class TableConfig(
  tableMetadata: TableMetadata,
  tableClassName: String,
  modelClassName: String,
  columns: List[ColumnConfig])

/** Generates [[TableConfig]]s (and their [[ColumnConfig]]s) by reading database metadata. Extend this directly or
  * indirectly, and override methods freely to customize.
  *
  * The default implementation generates code that does not use `slick-additions`, uses camelCase for corresponding
  * snake_case names in the database, and names model classes by appending `Row` to the camel-cased table name.
  */
class GenerationRules {
  private val logger = LoggerFactory.getLogger(getClass)

  def extraImports = List.empty[String]

  // noinspection ScalaWeakerAccess,ScalaUnusedSymbol
  protected def includeTable(table: MTable): Boolean = true

  // noinspection ScalaWeakerAccess
  protected def namingRules: NamingRules = NamingRules.ModelSuffixedWithRow

  // noinspection ScalaWeakerAccess
  final protected def columnNameToIdentifier(name: String)      = namingRules.columnNameToIdentifier(name)
  // noinspection ScalaWeakerAccess
  final protected def tableNameToIdentifier(name: MQName)       = namingRules.tableNameToIdentifier(name)
  final protected def modelClassName(tableName: MQName): String = namingRules.modelClassName(tableName)

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
  def baseColumnType(currentTableMetadata: TableMetadata, all: Seq[TableMetadata]): PartialFunction[MColumn, Type] = {
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
  protected def baseColumnDefault(currentTableMetadata: TableMetadata, all: Seq[TableMetadata])
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

  // noinspection ScalaWeakerAccess
  def columnConfig(column: MColumn, currentTableMetadata: TableMetadata, all: Seq[TableMetadata]): ColumnConfig = {
    val ident    = Term.Name(columnNameToIdentifier(column.name))
    val typ0     = baseColumnType(currentTableMetadata, all).applyOrElse(
      column,
      (_: MColumn) => {
        logger.warn(
          s"Column type not matched by `baseColumnType` for column: ${currentTableMetadata.table.name} ${column.name}"
        )
        typ"Nothing"
      }
    )
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

  def columnConfigs(currentTableMetadata: TableMetadata, all: Seq[TableMetadata]) =
    currentTableMetadata.columns.toList.map(columnConfig(_, currentTableMetadata, all))

  def tableConfig(currentTableMetadata: TableMetadata, all: Seq[TableMetadata]) =
    TableConfig(
      tableMetadata = currentTableMetadata,
      tableClassName = tableNameToIdentifier(currentTableMetadata.table.name),
      modelClassName = modelClassName(currentTableMetadata.table.name),
      columns = columnConfigs(currentTableMetadata, all)
    )

  def tableConfigs(slickProfileClass: Class[_ <: JdbcProfile])(implicit ec: ExecutionContext)
    : DBIO[List[TableConfig]] = {
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
                           } yield TableMetadata(
                             table = t,
                             columns = cols,
                             primaryKeys = pks,
                             foreignKeys = fks
                           )
                         }
                       )
    } yield infos.map(tableConfig(_, infos))
  }
}
