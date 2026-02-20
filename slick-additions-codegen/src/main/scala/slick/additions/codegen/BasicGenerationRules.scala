package slick.additions.codegen

class BasicGenerationRules extends GenerationRules {
  override type ObjectConfigType = TableConfig

  override protected def objectConfig(tableMetadata: GenerationRules.TableMetadata): TableConfig =
    TableConfig(tableMetadata.table.name, columnConfigs(tableMetadata), namingRules)
}
