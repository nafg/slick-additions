package slick.additions.codegen

import scala.meta.{Lit, Mod, Stat}

import slick.additions.codegen.ScalaMetaDsl.{
  defClass, defVal, init, scalametaNonMacroInterpolators, scalametaTermExtensionMethods, scalametaTypeExtensionMethods,
  termParam
}
import slick.jdbc.meta.MQName

import org.slf4j.LoggerFactory


/** Per-table code generator that produces a standard Slick table definition (a `Table` subclass and `TableQuery` val).
  *
  * @see
  *   [[EntityTableModulesObjectCodeGenerator]] for the slick-additions `EntityTableModule` variant
  */
class TablesObjectCodeGenerator(protected val tableConfig: TableConfig) extends SlickObjectCodeGenerator {
  private val logger = LoggerFactory.getLogger(getClass)

  // noinspection ScalaWeakerAccess
  def isDefaultSchema(schema: String) = schema == "public"

  def statements: List[Stat] =
    tableConfig match {
      case TableConfig(tableName, tableClassName, modelClassName, columns) =>
        val fields        = columns.map(columnField)
        val mapping       = mkMapping(modelClassName, "*", columns)
        tableName.catalog.foreach { catalog =>
          logger.warn(s"Ignoring catalog ($catalog)")
        }
        val params        = tableName match {
          case MQName(_, Some(schema), name) if !isDefaultSchema(schema) =>
            List(term"Some".termApply(Lit.String(schema)), Lit.String(name))
          case MQName(_, _, name)                                        =>
            List(Lit.String(name))
        }
        val tableClassDef =
          defClass(
            tableClassName,
            params = List(termParam(term"_tableTag", typ"Tag")),
            inits = List(init(typ"Table".typeApply(typ"$modelClassName"), List(List(term"_tableTag") ++ params)))
          )(
            fields :+ mapping
          )
        val tableQuery    =
          defVal(term"$tableClassName", modifiers = List(Mod.Lazy()))(
            term"TableQuery".termApplyType(typ"$tableClassName")
          )
        List(tableClassDef, tableQuery)
    }
}
