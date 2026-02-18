package slick.additions.codegen

import scala.meta.{Term, Type}

import slick.jdbc.meta.{MColumn, MQName}


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
  * @param tableName
  *   the name of the table this is for
  * @param tableClassName
  *   the name of the Slick table definition
  * @param modelClassName
  *   the name of the model class
  * @param columns
  *   configurations for this table's columns
  */
case class TableConfig(
  tableName: MQName,
  tableClassName: String,
  modelClassName: String,
  columns: List[ColumnConfig])
object TableConfig {
  def apply(tableName: MQName, columns: List[ColumnConfig], namingRules: NamingRules): TableConfig =
    TableConfig(
      tableName = tableName,
      tableClassName = namingRules.tableClassName(tableName),
      modelClassName = namingRules.modelClassName(tableName),
      columns = columns
    )
}
