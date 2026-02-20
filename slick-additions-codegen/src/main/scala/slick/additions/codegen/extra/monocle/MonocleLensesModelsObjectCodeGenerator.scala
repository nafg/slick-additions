package slick.additions.codegen.extra.monocle

import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*
import slick.additions.codegen.{BaseModelsObjectCodeGenerator, ColumnConfig, ModelsFileCodeGenerator}


/** Adds Monocle `GenLens`-based lenses for each model field to the companion object.
  *
  * Generated code requires `monocle-macro`.
  */
trait MonocleLensesModelsObjectCodeGenerator extends BaseModelsObjectCodeGenerator {

  /** The val name for a generated lens. Defaults to the field name (e.g., field `name` produces
    * `val name: Lens[Model, String]`).
    *
    * @example
    *   To prefix lens names with "lens" and capitalize:
    *   {{{
    * override protected def lensName(col: ColumnConfig) =
    *   "lens" + col.modelFieldTerm.value.capitalize
    *   }}}
    */
  protected def lensName(col: ColumnConfig): String = col.modelFieldTerm.value

  override protected def modelObjectStatements: List[Stat] = {
    val modelType = typ"$modelClassName"
    val lenses    = columnConfigs.map { col =>
      defVal(
        term"${lensName(col)}",
        Some(typ"Lens".typeApply(modelType, col.scalaType))
      )(
        term"GenLens".termApplyType(modelType)
          .termApply(Term.AnonymousFunction(Term.Placeholder().termSelect(col.modelFieldTerm)))
      )
    }
    super.modelObjectStatements ++ lenses
  }
}
trait MonocleLensesModelsFileCodeGenerator extends ModelsFileCodeGenerator {
  override def imports =
    super.imports ++ List(
      "monocle.Lens",
      "monocle.macros.GenLens"
    )
}
