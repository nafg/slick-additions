package slick.additions.codegen

import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.{defClass, makeImports, termParam}
import slick.jdbc.JdbcProfile


/** Code generator that produces a case class for each table to represent a row in code
  */
trait ModelsCodeGenerator extends BaseCodeGenerator {
  protected def columnConfigs(tableConfig: TableConfig) = tableConfig.columns

  protected def modelClass(tableConfig: TableConfig) =
    defClass(
      tableConfig.modelClassName,
      modifiers = List(Mod.Case()),
      params =
        columnConfigs(tableConfig).map { col =>
          termParam(col.modelFieldTerm, col.scalaType, default = col.scalaDefault)
        }
    )()

  protected def tableStats(tableConfig: TableConfig): List[Stat] = List(modelClass(tableConfig))

  protected def allImports(extraImports: List[String], slickProfileClass: Class[? <: JdbcProfile]): List[Stat] =
    makeImports(imports ++ extraImports)
}
