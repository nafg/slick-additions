package slick.additions.codegen

import scala.meta.{Init, Mod, Name, Type}


/**
 * Annotates model classes with Circe's `@JsonCodec`.
 *
 * Generated code requires `circe-generic`
 */
trait CirceJsonCodecModelsCodeGenerator extends ModelsCodeGenerator {
  override protected def modelClass(tableConfig: TableConfig) = {
    val original = super.modelClass(tableConfig)
    original.copy(mods = Mod.Annot(Init(Type.Name("JsonCodec"), Name.Anonymous(), Nil)) +: original.mods)
  }

  override protected def imports(strings: List[String]) =
    super.imports(strings :+ "io.circe.generic.JsonCodec")
}
