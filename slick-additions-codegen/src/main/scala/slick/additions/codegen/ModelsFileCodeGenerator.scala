package slick.additions.codegen

import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.{defClass, termParam}


/** Per-table code generator that produces a model case class for one table.
  *
  * @see
  *   [[KeylessModelsObjectCodeGenerator]] which filters out primary key columns
  */
class ModelsObjectCodeGenerator(protected val tableConfig: TableConfig) extends ObjectCodeGenerator {
  protected def columnConfigs = tableConfig.columns

  protected def modelClass =
    defClass(
      tableConfig.modelClassName,
      modifiers = List(Mod.Case()),
      params =
        columnConfigs.map { col =>
          termParam(col.modelFieldTerm, col.scalaType, default = col.scalaDefault)
        }
    )()

  def statements: List[Stat] = List(modelClass)
}

/** Code generator that produces a case class for each table to represent a row in code
  */
trait ModelsFileCodeGenerator extends FileCodeGenerator {
  def objectCodeGenerator(tableConfig: TableConfig): ModelsObjectCodeGenerator =
    new ModelsObjectCodeGenerator(tableConfig)
}
