package slick.additions.codegen


/**
 * Omits the primary key field from generated model classes, unless the primary key isn't a single column.
 */
class KeylessModelsCodeGenerator extends ModelsCodeGenerator {
  override protected def columnConfigs(tableConfig: TableConfig) =
    super.columnConfigs(tableConfig)
      .filter(c => Seq(c.column.name) != tableConfig.tableMetadata.primaryKeys.map(_.column))
}
