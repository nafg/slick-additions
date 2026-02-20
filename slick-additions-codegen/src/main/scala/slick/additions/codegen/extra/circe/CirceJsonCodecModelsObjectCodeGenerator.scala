package slick.additions.codegen.extra.circe

import scala.meta.{Mod, Stat}

import slick.additions.codegen.ScalaMetaDsl.*
import slick.additions.codegen.{BaseModelsObjectCodeGenerator, ModelsFileCodeGenerator}


/** Adds semi-auto derived Circe `Encoder` and `Decoder` instances to the model companion object.
  *
  * Generated code requires `circe-generic`.
  */
//noinspection ScalaUnusedSymbol
trait CirceJsonCodecModelsObjectCodeGenerator extends BaseModelsObjectCodeGenerator {
  override protected def modelObjectStatements: List[Stat] = {
    val modelType = typ"$modelClassName"

    def deriver(prefix: String, typeClass: String, method: String) =
      defVal(
        name = term"${prefix + modelClassName}",
        declaredType = Some(typ"$typeClass".typeApply(modelType)),
        modifiers = Seq(Mod.Implicit())
      )(
        term"$method".termApplyType(modelType)
      )

    super.modelObjectStatements ++ List(
      deriver("encode", "Encoder", "deriveEncoder"),
      deriver("decode", "Decoder", "deriveDecoder")
    )
  }
}
//noinspection ScalaUnusedSymbol
trait CirceJsonCodecModelsFileCodeGenerator   extends ModelsFileCodeGenerator       {
  override def imports =
    super.imports ++ List(
      "io.circe.{Decoder, Encoder}",
      "io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}"
    )
}
