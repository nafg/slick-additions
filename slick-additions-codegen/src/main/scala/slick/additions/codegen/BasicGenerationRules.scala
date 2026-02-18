package slick.additions.codegen

class BasicGenerationRules extends GenerationRules {
  override type ObjectConfigType = TableConfig

  override protected def objectConfig(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  ): TableConfig =
    TableConfig(
      currentTableMetadata.table.name,
      columnConfigs(currentTableMetadata, all),
      namingRules
    )
}
