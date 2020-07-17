package slick.additions.codegen

import scala.concurrent.ExecutionContext
import scala.meta._

import slick.jdbc.JdbcProfile


/**
 * Code generator that produces a case class for each table to represent a row in code
 */
class ModelsCodeGenerator extends BaseCodeGenerator {
  protected def columnConfigs(tableConfig: TableConfig) = tableConfig.columns

  protected def modelClass(tableConfig: TableConfig) = {
    val params = columnConfigs(tableConfig).map { col =>
      Term.Param(Nil, col.modelFieldTerm, Some(col.scalaType), col.scalaDefault)
    }

    q"case class ${Type.Name(tableConfig.modelClassName)}(..$params)"
  }

  protected def rowStats(tableConfig: TableConfig): List[Stat] =
    List(modelClass(tableConfig))

  override def codeString(rules: GenerationRules, slickProfileClass: Class[_ <: JdbcProfile])
                         (implicit executionContext: ExecutionContext) =
    rules.tableConfigs(slickProfileClass).map { tableConfigs =>
      q"""
        package ${toTermRef(rules.packageName)} {
          ..${imports(rules.extraImports)}

          ..${tableConfigs.flatMap(rowStats)}
        }
       """.syntax
    }
}
