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

  override protected def tableStats(tableConfig: TableConfig) =
    List(
      modelClass(tableConfig),
      defObject(Term.Name(tableConfig.modelClassName))()
    )

  override def imports = super.imports :+ "monocle.macros.Lenses"
}
