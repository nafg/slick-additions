package slick.additions.codegen.extra.circe

import scala.meta.Mod

import slick.additions.codegen.ScalaMetaDsl._
import slick.additions.codegen.{ModelsCodeGenerator, TableConfig}


/** Annotates model classes with Circe's `@JsonCodec`.
  *
  * Generated code requires `circe-generic`
  */
trait CirceJsonCodecModelsCodeGenerator extends ModelsCodeGenerator {
  override protected def modelClass(tableConfig: TableConfig) =
    super
      .modelClass(tableConfig)
      .withMod(Mod.Annot(init(typ"JsonCodec")))

  override protected def imports(strings: List[String]) = super.imports(strings :+ "io.circe.generic.JsonCodec")
}
