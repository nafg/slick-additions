package slick.additions.codegen.extra.monocle

import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*
import slick.additions.codegen.{ModelsFileCodeGenerator, ModelsObjectCodeGenerator, TableConfig}


/** Annotates model classes with Monocle's `@Lenses`.
  *
  * Generated code requires `monocle-macro`
  */
trait MonocleLensesModelsObjectCodeGenerator extends ModelsObjectCodeGenerator {
  override protected def modelClass =
    super
      .modelClass
      .withMod(Mod.Annot(init(typ"Lenses")))

  override def statements =
    List(
      modelClass,
      defObject(Term.Name(tableConfig.modelClassName))()
    )
}
trait MonocleLensesModelsFileCodeGenerator  extends ModelsFileCodeGenerator  {
  override def imports = super.imports :+ "monocle.macros.Lenses"
}
