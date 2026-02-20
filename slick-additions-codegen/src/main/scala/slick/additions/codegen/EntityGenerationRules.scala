package slick.additions.codegen

import slick.additions.codegen.ScalaMetaDsl.*
import slick.jdbc.meta.MQName


trait LookupColumnGenerationRules extends GenerationRules {
  override protected def baseColumnType =
    Function.unlift { column =>
      super.baseColumnType.lift(column).map { keyType =>
        column.foreignKey match {
          case None     => keyType
          case Some(fk) =>
            term"slick".termSelect("additions").termSelect("entity")
              .typeSelect(typ"Lookup")
              .typeApply(keyType, typ"${namingRules.modelClassName(fk.pkTable)}")
        }
      }
    }
}

/** Uses `slick-additions-entity` `Lookup` for foreign key fields.
  *
  * Generated code requires `slick-additions-entity`.
  */
trait EntityGenerationRules extends LookupColumnGenerationRules {
  override type ObjectConfigType = EntityGenerationRules.ObjectConfig

  override protected def objectConfig(tableMetadata: GenerationRules.TableMetadata)
    : EntityGenerationRules.ObjectConfig = {
    val columns = columnConfigs(tableMetadata)
    EntityGenerationRules.partitionPrimaryKey(tableMetadata, columns) match {
      case (Seq(pk), nonPkColumns) =>
        EntityGenerationRules.ObjectConfig.EntityTable(
          tableMetadata.table.name,
          pk,
          nonPkColumns,
          namingRules
        )
      case _                       =>
        EntityGenerationRules.ObjectConfig.BasicTable(TableConfig(tableMetadata.table.name, columns, namingRules))
    }
  }

}
object EntityGenerationRules {
  // noinspection ScalaWeakerAccess
  def partitionPrimaryKey(tableMetadata: GenerationRules.TableMetadata, columns: List[ColumnConfig])
    : (List[ColumnConfig], List[ColumnConfig]) =
    columns.partition(c => tableMetadata.primaryKeys.exists(_.column == c.column.name))

  sealed trait ObjectConfig
  object ObjectConfig {
    case class BasicTable(tableConfig: TableConfig) extends ObjectConfig
    case class EntityTable(
      tableClassName: String,
      modelClassName: String,
      keyColumn: ColumnConfig,
      mappedColumns: List[ColumnConfig]) extends ObjectConfig
    object EntityTable {
      def apply(tableName: MQName, keyColumn: ColumnConfig, mappedColumns: List[ColumnConfig], namingRules: NamingRules)
        : EntityTable =
        EntityTable(
          tableClassName = namingRules.tableClassName(tableName),
          modelClassName = namingRules.modelClassName(tableName),
          keyColumn = keyColumn,
          mappedColumns = mappedColumns
        )
    }
  }
}
