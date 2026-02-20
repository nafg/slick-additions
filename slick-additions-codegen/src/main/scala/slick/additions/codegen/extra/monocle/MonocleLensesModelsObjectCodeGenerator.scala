package slick.additions.codegen.extra.monocle

import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*
import slick.additions.codegen.{BaseModelsObjectCodeGenerator, ModelsFileCodeGenerator}


/** Annotates model classes with Monocle's `@Lenses`.
  *
  * Generated code requires `monocle-macro`
  */
trait MonocleLensesModelsObjectCodeGenerator extends BaseModelsObjectCodeGenerator {
  override protected def modelClass =
    super
      .modelClass
      .withMod(Mod.Annot(init(typ"Lenses")))

  override def statements =
    List(
      modelClass,
      defObject(Term.Name(modelClassName))()
    )
}
trait MonocleLensesModelsFileCodeGenerator   extends ModelsFileCodeGenerator       {
  override def imports = super.imports :+ "monocle.macros.Lenses"
}
