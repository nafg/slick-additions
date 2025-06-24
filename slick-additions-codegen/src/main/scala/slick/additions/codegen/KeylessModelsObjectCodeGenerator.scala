package slick.additions.codegen

/** Omits the primary key field from generated model classes, unless the primary key isn't a single column.
  */
class KeylessModelsObjectCodeGenerator(tableConfig: TableConfig) extends ModelsObjectCodeGenerator(tableConfig) {
  override protected def columnConfigs =
    super.columnConfigs
      .filter(c => Seq(c.column.name) != tableConfig.tableMetadata.primaryKeys.map(_.column))
}
