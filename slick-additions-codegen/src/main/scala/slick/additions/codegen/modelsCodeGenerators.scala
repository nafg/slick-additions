package slick.additions.codegen

/** Code generator that produces a case class for each table to represent a row in code
  */
trait ModelsFileCodeGenerator extends BasicFileCodeGenerator {
  override protected def objectCodeGenerator(tableConfig: TableConfig) = new ModelsObjectCodeGenerator(tableConfig)
}

trait EntityModelsFileCodeGenerator extends EntityFileCodeGenerator {
  override protected def objectCodeGenerator(config: EntityGenerationRules.ObjectConfig) =
    config match {
      case config: EntityGenerationRules.ObjectConfig.EntityTable => new EntityModelsObjectCodeGenerator(config)
      case EntityGenerationRules.ObjectConfig.BasicTable(config)  => new ModelsObjectCodeGenerator(config)
    }
}

class ModelsObjectCodeGenerator(tableConfig: TableConfig)
    extends BaseModelsObjectCodeGenerator(tableConfig.modelClassName, tableConfig.columns)

/** Omits the primary key field from generated model classes, unless the primary key isn't a single column.
  */
class EntityModelsObjectCodeGenerator(config: EntityGenerationRules.ObjectConfig.EntityTable)
    extends BaseModelsObjectCodeGenerator(config.modelClassName, config.mappedColumns)
