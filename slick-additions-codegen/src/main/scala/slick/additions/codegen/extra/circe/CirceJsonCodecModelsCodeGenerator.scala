package slick.additions.codegen.extra.circe

import scala.meta.{Init, Mod, Name, Type}

import slick.additions.codegen.{
  ModelsCodeGenerator,
  TableConfig,
  scalametaDefnClassExtensionMethods
}

/** Annotates model classes with Circe's `@JsonCodec`.
  *
  * Generated code requires `circe-generic`
  */
trait CirceJsonCodecModelsCodeGenerator extends ModelsCodeGenerator {
  override protected def modelClass(tableConfig: TableConfig) =
    super
      .modelClass(tableConfig)
      .withMod(Mod.Annot(Init(Type.Name("JsonCodec"), Name.Anonymous(), Nil)))

  override protected def imports(strings: List[String]) =
    super.imports(strings :+ "io.circe.generic.JsonCodec")
}
