package slick.additions.codegen

import scala.concurrent.ExecutionContext
import scala.meta._

import slick.additions.codegen.ScalaMetaDsl.{defClass, termParam}
import slick.jdbc.JdbcProfile


/** Code generator that produces a case class for each table to represent a row in code
  */
class ModelsCodeGenerator extends BaseCodeGenerator {
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

  protected def rowStats(tableConfig: TableConfig): List[Stat] = List(modelClass(tableConfig))

  override def codeString(
    rules: GenerationRules,
    slickProfileClass: Class[_ <: JdbcProfile]
  )(implicit executionContext: ExecutionContext
  ) =
    rules.tableConfigs(slickProfileClass).map { tableConfigs =>
      val packageRef       = toTermRef(rules.packageName)
      val importStatements = imports(rules.extraImports)
      val tableRowStats    = tableConfigs.flatMap(rowStats)

      Pkg(
        ref = packageRef,
        stats = importStatements ++ tableRowStats
      ).syntax
    }
}
