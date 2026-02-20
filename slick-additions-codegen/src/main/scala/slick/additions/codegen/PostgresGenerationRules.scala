package slick.additions.codegen

import java.sql.Types

import scala.meta.{Lit, Term, Type}

import slick.additions.codegen.ScalaMetaDsl.{
  scalametaNonMacroInterpolators, scalametaTermExtensionMethods, scalametaTermRefExtensionMethods,
  scalametaTypeExtensionMethods
}


/** Adds PostgreSQL-specific type mappings and default value handlers.
  *
  * Maps Postgres type aliases (`int4`, `text`, `bool`, `float8`, `timestamp`, etc.) to Scala types, and handles
  * Postgres-style defaults (`now()`, boolean literals, etc.).
  *
  * Also strips the `_` prefix from array type names (e.g. `_int4` becomes `int4`) so that the element type resolves
  * correctly via [[baseColumnTypeMapping]].
  *
  * @see
  *   [[PostgresArrayGenerationRules]] for wrapping array columns in `List[...]`
  */
trait PostgresGenerationRules extends GenerationRules {
  override protected def baseColumnTypeMapping =
    super.baseColumnTypeMapping.orElse {
      case "float8"                    => typ"Double"
      case "int4" | "serial"           => typ"Int"
      case "int8" | "bigserial"        => typ"Long"
      case "bool"                      => typ"Boolean"
      case "text"                      => typ"String"
      case "timestamp" | "timestamptz" => typ"Instant"
      case "time" | "timetz"           => typ"LocalTime"
      case "lo"                        => term"java".termSelect(term"sql").typeSelect(typ"Blob")
    }

  final protected def isArray(column: GenerationRules.ColumnMetadata) = column.typeNameLower.startsWith("_")

  override protected def baseColumnType: PartialFunction[GenerationRules.ColumnMetadata, Type] =
    Function.unlift(col => baseColumnTypeMapping.lift(col.typeNameLower.stripPrefix("_")))

  override protected def baseColumnDefault =
    super.baseColumnDefault.orElse {
      case ColType(_, "bool", Some(AsBoolean(b)))                                    => Lit.Boolean(b)
      case ColType(_, "int4" | "serial", Some(AsInt(i)))                             => Lit.Int(i)
      case ColType(_, "int8" | "bigserial", Some(AsLong(l)))                         => Lit.Long(l)
      case ColType(_, "float8", Some(AsDouble(d)))                                   => Lit.Double(d)
      case ColType(_, "text", Some(s))                                               => Lit.String(s.stripPrefix("'").stripSuffix("'"))
      case ColType(_, "timestamp" | "timestamptz", Some("now()" | "LOCALTIMESTAMP")) =>
        term"java".termSelect("time").termSelect("Instant").termSelect("now").termApply()
    }
}

/** Extends [[PostgresGenerationRules]] to wrap PostgreSQL array columns in `List[...]`.
  *
  * Array columns are identified by the `_` prefix on their type name (e.g. `_int4` for `integer[]`). Override
  * [[GenerationRules.transformColumnType]] to use a different collection type.
  */
//noinspection ScalaUnusedSymbol
trait PostgresArrayGenerationRules extends PostgresGenerationRules {
  override protected def transformColumnType(column: GenerationRules.ColumnMetadata, baseType: Type) =
    if (isArray(column))
      typ"List".typeApply(baseType)
    else
      super.transformColumnType(column, baseType)
}
