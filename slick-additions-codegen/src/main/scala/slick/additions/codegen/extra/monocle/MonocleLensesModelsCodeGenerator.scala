package slick.additions.codegen.extra.monocle

import scala.meta._

import slick.additions.codegen.{
  ModelsCodeGenerator,
  TableConfig,
  scalametaDefnClassExtensionMethods
}

/** Annotates model classes with Monocle's `@Lenses`.
  *
  * Generated code requires `monocle-macro`
  */
trait MonocleLensesModelsCodeGenerator extends ModelsCodeGenerator {
  override protected def modelClass(tableConfig: TableConfig) =
    super
      .modelClass(tableConfig)
      .withMod(Mod.Annot(Init(Type.Name("Lenses"), Name.Anonymous(), Nil)))

  override protected def rowStats(tableConfig: TableConfig) =
    List(
      modelClass(tableConfig),
      q"object ${Term.Name(tableConfig.modelClassName)}"
    )

  override protected def imports(strings: List[String]) =
    super.imports(strings :+ "monocle.macros.Lenses")
}
