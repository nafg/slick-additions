package slick.additions.codegen

import scala.annotation.tailrec
import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MQName


/** Code generator for standard Slick table definitions. The generated code has no dependency on slick-additions.
  *
  * Tables that have more than 22 fields are mapped by simply nesting tuples so that no single tuple has more than 22
  * elements.
  */
trait TablesFileCodeGenerator extends FileCodeGenerator {
  protected def profileImport(slickProfileClass: Class[_ <: JdbcProfile]): List[Stat] = {
    val profileName = toTermRef(slickProfileClass.getName.stripSuffix("$"))
    List(
      Import(List(Importer(profileName.termSelect("api"), List(Importee.Wildcard()))))
    )
  }

  override protected def importStatements(extraImports: List[String], slickProfileClass: Class[? <: JdbcProfile])
    : List[Stat] =
    profileImport(slickProfileClass) ++
      super.importStatements(extraImports, slickProfileClass)

  def objectCodeGenerator(tableConfig: TableConfig): TablesObjectCodeGenerator =
    new TablesObjectCodeGenerator(tableConfig)
}

/** Per-table code generator that produces a standard Slick table definition (a `Table` subclass and `TableQuery` val).
  *
  * @see
  *   [[EntityTableModulesObjectCodeGenerator]] which extends this to use slick-additions `EntityTableModule`
  */
class TablesObjectCodeGenerator(protected val tableConfig: TableConfig) extends ObjectCodeGenerator {
  // noinspection ScalaWeakerAccess
  def isDefaultSchema(schema: String) = schema == "public"

  // noinspection ScalaWeakerAccess
  protected def mappingType(rowClassType: Type.Name) =
    term"slick".termSelect("lifted").typeSelect(typ"ProvenShape")
      .typeApply(rowClassType)

  def mkMapping(rowClassName: String, mappingName: String, columns: List[ColumnConfig]) = {
    val companion    = Term.Name(rowClassName)
    val rowClassType = Type.Name(rowClassName)
    val terms        = columns.map(_.tableFieldTerm)
    val numCols      = columns.length
    val rhs          =
      if (numCols == 1)
        terms.head
          .termSelect("mapTo")
          .termApplyType(rowClassType)
      else if (numCols <= 22)
        Term.Tuple(terms)
          .termSelect("mapTo")
          .termApplyType(rowClassType)
      else {
        @tailrec
        def group22[A](values: List[A])(group: List[A] => A): A =
          values match {
            case List(one) => one
            case _         =>
              val (first, second) = values.splitAt(22)
              group22(group(first) +: second)(group)
          }

        val pat = group22[Pat](terms.map(Pat.Var(_)))(Pat.Tuple(_))
        val fac =
          Term.PartialFunction(
            List(
              Case(pat, None, companion.termApply(terms: _*))
            )
          )

        val res = group22[Term](terms.map(t => term"rec".termSelect(t)))(Term.Tuple(_))

        val extractor =
          Term.Function(
            Term.ParamClause(
              List(termParam(term"rec", Type.Name(rowClassName)))
            ),
            term"Some".termApply(res)
          )

        group22[Term](terms)(Term.Tuple(_))
          .termSelect("<>")
          .termApply(fac, extractor)
      }

    defDef(mappingName, declaredType = Some(mappingType(rowClassType)))()(
      rhs
    )
  }

  def columnField: ColumnConfig => Stat = {
    case ColumnConfig(column, tableFieldName, _, scalaType, _) =>
      defVal(tableFieldName)(
        term"column"
          .termApplyType(scalaType)
          .termApply(Lit.String(column.name))
      )
  }

  def statements: List[Stat] =
    tableConfig match {
      case TableConfig(tableMetadata, tableClassName, modelClassName, columns) =>
        val fields        = columns.map(columnField)
        val mapping       = mkMapping(modelClassName, "*", columns)
        tableMetadata.table.name.catalog.foreach { catalog =>
          println(s"Warning: ignoring catalog ($catalog)")
        }
        val params        = tableMetadata.table.name match {
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
