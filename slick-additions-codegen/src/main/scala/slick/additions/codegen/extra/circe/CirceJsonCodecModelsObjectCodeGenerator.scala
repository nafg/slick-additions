package slick.additions.codegen.extra.circe

import scala.meta.Mod

import slick.additions.codegen.ScalaMetaDsl.*
import slick.additions.codegen.{ModelsFileCodeGenerator, ModelsObjectCodeGenerator, TableConfig}


/** Annotates model classes with Circe's `@JsonCodec`.
  *
  * Generated code requires `circe-generic`
  */
//noinspection ScalaUnusedSymbol
trait CirceJsonCodecModelsObjectCodeGenerator extends ModelsObjectCodeGenerator {
  override protected def modelClass =
    super
      .modelClass
      .withMod(Mod.Annot(init(typ"JsonCodec")))

}
//noinspection ScalaUnusedSymbol
trait CirceJsonCodecModelsFileCodeGenerator extends ModelsFileCodeGenerator {
  override def imports = super.imports :+ "io.circe.generic.JsonCodec"
}
