package slick.additions.codegen

import java.sql.Types

import slick.additions.codegen.ScalaMetaDsl.{scalametaNonMacroInterpolators, scalametaTypeExtensionMethods}


trait PostgresGenerationRules extends GenerationRules {
  override protected def baseColumnType(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  ) =
    super.baseColumnType(currentTableMetadata, all)
      .orElse {
        case ColType(Types.TIMESTAMP, "timestamp", _) => typ"Instant"
        case ColType(Types.TIME, "time", _)           => typ"LocalTime"
      }
}

//noinspection ScalaUnusedSymbol
trait PostgresArrayGenerationRules extends PostgresGenerationRules {
  override protected def baseColumnType(
    currentTableMetadata: GenerationRules.TableMetadata,
    all: Seq[GenerationRules.TableMetadata]
  ) =
    super.baseColumnType(currentTableMetadata, all)
      .orElse {
        case col @ ColType(Types.ARRAY, name, _) if name.startsWith("_") =>
          val elementType = baseColumnType(currentTableMetadata, all, col.copy(typeName = name.stripPrefix("_")))
          typ"List".typeApply(elementType)
      }
}
