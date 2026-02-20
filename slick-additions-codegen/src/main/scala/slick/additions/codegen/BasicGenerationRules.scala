package slick.additions.codegen

/** Concrete [[GenerationRules]] that produces [[TableConfig]] objects for standard Slick table definitions.
  *
  * @see
  *   [[EntityGenerationRules]] for entity-aware code generation
  */
class BasicGenerationRules extends GenerationRules {
  override type ObjectConfigType = TableConfig

  override protected def objectConfig(tableMetadata: GenerationRules.TableMetadata): TableConfig =
    TableConfig(tableMetadata.table.name, columnConfigs(tableMetadata), namingRules)
}
