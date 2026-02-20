package slick.additions.codegen

import scala.annotation.tailrec
import scala.meta.{Case, Lit, Pat, Stat, Term, Type}

import slick.additions.codegen.ScalaMetaDsl.{
  defDef, defVal, scalametaNonMacroInterpolators, scalametaTermExtensionMethods, scalametaTermRefExtensionMethods,
  scalametaTypeExtensionMethods, termParam
}


trait SlickObjectCodeGenerator extends ObjectCodeGenerator {
  def columnField: ColumnConfig => Stat = {
    case ColumnConfig(column, tableFieldName, _, scalaType, _) =>
      defVal(tableFieldName)(
        term"column"
          .termApplyType(scalaType)
          .termApply(Lit.String(column.name))
      )
  }

  // noinspection ScalaWeakerAccess
  protected def mappingType(rowClassType: Type.Name): Type.Apply =
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
}
