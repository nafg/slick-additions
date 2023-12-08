package slick.additions.codegen.extra.monocle

import scala.meta._

import slick.additions.codegen.ScalaMetaDsl._
import slick.additions.codegen.{ModelsCodeGenerator, TableConfig}


/** Annotates model classes with Monocle's `@Lenses`.
  *
  * Generated code requires `monocle-macro`
  */
trait MonocleLensesModelsCodeGenerator extends ModelsCodeGenerator {
  override protected def modelClass(tableConfig: TableConfig) =
    super
      .modelClass(tableConfig)
      .withMod(Mod.Annot(init(typ"Lenses")))

  override protected def rowStats(tableConfig: TableConfig) =
    List(
      modelClass(tableConfig),
      defObject(Term.Name(tableConfig.modelClassName))()
    )

  override protected def imports(strings: List[String]) = super.imports(strings :+ "monocle.macros.Lenses")
}
