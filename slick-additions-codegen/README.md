# slick-additions-codegen

[![javadoc](https://javadoc.io/badge2/io.github.nafg/slick-additions-codegen_2.13/javadoc.svg)](https://javadoc.io/doc/io.github.nafg/slick-additions-codegen_2.13)

A code generator for [Slick](https://scala-slick.org) that reads your database schema and produces Scala source files.
Built on [Scalameta](https://scalameta.org/) for AST construction and [Scalafmt](https://scalameta.org/scalafmt/) for formatting.

## Why use this instead of Slick's built-in codegen?

Slick ships with a code generator, but it uses string concatenation to build code and produces
everything in a single monolithic file — model case classes, table definitions, and profile setup are
all tangled together. This makes it hard to use the generated models outside of Slick (e.g. in a
Scala.js frontend or a shared module), and customization means overriding methods deep in a large
class hierarchy.

`slick-additions-codegen` takes a different approach:

- **Separate model and table generation** — models are generated as plain case classes with no Slick
  dependency, so they can be used anywhere: shared modules, Scala.js frontends, API definitions, etc.
  Table definitions are generated separately.
- **Scalameta-based** — code is constructed as a typed AST rather than string concatenation, so output
  is always syntactically valid
- **Composable architecture** — small, focused traits that you mix and match. Each `ObjectCodeGenerator`
  produces the code for one table, and `FileCodeGenerator` assembles them into a formatted file. You
  can write your own `ObjectCodeGenerator` to generate anything from database metadata — not just
  Slick tables.
- **Customizable at every level** — type mappings, default values, naming conventions, column filtering,
  and per-table code generation are all overridable hooks
- **Entity-aware** — optional integration with `slick-additions-entity` types (`Entity`, `Lookup`,
  `EntityTableModule`), but the codegen works perfectly well on its own without entity support

## Setup

```scala
libraryDependencies += "io.github.nafg" %% "slick-additions-codegen" % "latest.release"
```

## Architecture

The codegen has three layers:

1. **Generation Rules** (`GenerationRules`) — read database metadata and produce config objects
   describing each table (type mappings, naming, column configs)
2. **Object Code Generators** (`ObjectCodeGenerator`) — take a config for one table and produce
   Scala statements (case classes, table definitions, module objects, etc.)
3. **File Code Generators** (`FileCodeGenerator`) — orchestrate object code generators for all tables
   and assemble the results into a formatted `.scala` file with imports

The `ObjectCodeGenerator` layer is intentionally general-purpose: the library provides generators for
model case classes and Slick table definitions, but you can implement your own to generate anything
that corresponds to your database schema.

### Generation Rules

`GenerationRules` is the base trait. It queries database metadata (tables, columns, primary keys,
foreign keys) and produces typed config objects. There are four areas of customization:

- **Filtering** — control which tables and columns to generate code for
- **Naming** — control how database names map to Scala identifiers (table class names, model class
  names, column field names)
- **Column types and defaults** — map SQL types to Scala types, handle database default values,
  and post-process types (e.g. wrapping array columns in `List[...]`)
- **Object config** — the abstract method that produces a per-table config object, determining
  what kind of code gets generated for each table

Two concrete variants are provided:

- **`BasicGenerationRules`** — produces `TableConfig` objects for standard Slick table definitions
- **`EntityGenerationRules`** — produces config objects that separate the primary key column from
  value columns, enabling integration with `slick-additions-entity` types (`Entity[K, V]`,
  `EntityTableModule`)

### Code Generators

`FileCodeGenerator` takes generation rules and produces formatted `.scala` files. Each file code
generator delegates to an `ObjectCodeGenerator` for each table. Several variants are provided:

| File Generator | Object Generator | What it produces |
|-----------|-----------------|---------|
| `ModelsFileCodeGenerator` | `ModelsObjectCodeGenerator` | Case classes for each table |
| `EntityModelsFileCodeGenerator` | `EntityModelsObjectCodeGenerator` | Case classes without PK field |
| `TablesFileCodeGenerator` | `TablesObjectCodeGenerator` | Standard Slick `Table` definitions |
| `EntityTableModulesFileCodeGenerator` | `EntityTableModulesObjectCodeGenerator` | `EntityTableModule` objects with custom profile |

## Quick Start

A minimal setup for a PostgreSQL database with entity-aware generation:

```scala
import slick.additions.codegen._

// 1. Define your generation rules
object MyRules extends PostgresGenerationRules with EntityGenerationRules

// 2. Define file code generators
object MyModelsCodegen extends EntityModelsFileCodeGenerator {
  override val generationRules = MyRules
  override def packageName = "myapp.models"
  override def filename = "models"
}

object MyTablesCodegen extends EntityTableModulesFileCodeGenerator {
  override val generationRules = MyRules
  override def packageName = "myapp.tables"
  override def filename = "tables"
}

// 3. Run codegen (e.g. from an sbt task or a main method)
import com.typesafe.config.ConfigFactory
val slickConfig = ConfigFactory.load().getConfig("slick.dbs.default")
MyModelsCodegen.writeToFileSync(baseDir, slickConfig)
MyTablesCodegen.writeToFileSync(baseDir, slickConfig)
```

## Customization Guide

The column type and default methods are named with a "base" prefix because they determine the
type *before* nullable wrapping — for nullable columns, the result is automatically wrapped in
`Option[...]`.

### Type Mappings

Override `baseColumnTypeMapping` to map SQL type names to Scala types.
Use `asFallbackFor` when your mappings should take priority over inherited ones:

```scala
override protected def baseColumnTypeMapping =
  super.baseColumnTypeMapping.asFallbackFor {
    case "interval" => typ"Duration"
    case "jsonb"    => typ"Json"
  }
```

For column-specific overrides (when you need access to table name, column name, etc.),
override `baseColumnType` instead:

```scala
override protected def baseColumnType =
  super.baseColumnType.asFallbackFor {
    case ColName("users", "role") => typ"UserRole"
  }
```

### Default Values

Override `baseColumnDefault` to handle database default values:

```scala
override protected def baseColumnDefault =
  super.baseColumnDefault.orElse {
    case ColType(_, "my_enum", Some(s)) => term"MyEnum".termSelect(s)
  }
```

### Type Transformations

Override `transformColumnType` for post-processing after the base type is resolved.
This runs before nullable wrapping, so it applies to the inner type:

```scala
override protected def transformColumnType(column: GenerationRules.ColumnMetadata, baseType: Type) =
  if (isArray(column)) typ"Seq".typeApply(baseType)
  else super.transformColumnType(column, baseType)
```

### Column and Table Filtering

```scala
override protected def includeTable(table: MTable) =
  table.name.name != "flyway_schema_history"

override protected def includeColumn(column: GenerationRules.ColumnMetadata) =
  column.name != "tenant_id"
```

### Naming Conventions

`NamingRules` is a trait you can implement to fully control how database names map to Scala
identifiers. Two built-in strategies are provided:

- **`NamingRules.ModelSuffixedWithRow`** (default) — table `line_items` becomes class `LineItems`, model `LineItemsRow`
- **`NamingRules.TablePluralModelSingular`** — table `line_item` becomes class `LineItems`, model `LineItem`

```scala
override protected def namingRules = NamingRules.TablePluralModelSingular
```

## Database-Specific Rules

### PostgreSQL

`PostgresGenerationRules` adds mappings for PostgreSQL-specific types (e.g. `int4` to `Int`,
`timestamptz` to `Instant`, `text` to `String`) and handles their default values. It also strips
the `_` prefix from array type names so that the element type resolves correctly.

### PostgreSQL Arrays

`PostgresArrayGenerationRules` extends `PostgresGenerationRules` and wraps array columns
(types starting with `_`) in `List[...]`:

```scala
object MyRules extends PostgresArrayGenerationRules with EntityGenerationRules
```

To use a different collection type for specific types, override `transformColumnType` and
delegate to `super` for the rest. Scalameta `Type` uses reference equality, so capture the
types you need to match as `val`s:

```scala
// Scalameta Type uses reference equality, so capture as a val to match on it
val DurationType = typ"Duration"

override protected def baseColumnTypeMapping =
  super.baseColumnTypeMapping.asFallbackFor {
    case "interval" => DurationType
  }

override protected def transformColumnType(column: GenerationRules.ColumnMetadata, baseType: Type) =
  baseType match {
    case DurationType if isArray(column) => typ"Seq".typeApply(baseType)
    case _                               => super.transformColumnType(column, baseType)
  }
```

## Model Companion Object Extras

### Circe JSON Codecs

Mix `CirceJsonCodecModelsObjectCodeGenerator` into your model object code generator to add
semi-auto derived `Encoder` and `Decoder` instances to each model's companion object.
For file-level imports, also mix in `CirceJsonCodecModelsFileCodeGenerator`.

Generated code requires `circe-generic`.

### Monocle Lenses

Mix `MonocleLensesModelsObjectCodeGenerator` into your model object code generator to add
`GenLens`-based lens vals to each model's companion object.
For file-level imports, also mix in `MonocleLensesModelsFileCodeGenerator`.

Override `lensName` to control the generated val name for each lens.
The default uses the field name directly (e.g. field `name` produces `val name: Lens[Model, String]`).

Generated code requires `monocle-macro`.

## Pattern Matching Extractors

The codegen package provides extractors for use in `baseColumnType` and `baseColumnDefault` pattern matches:

- **`ColType(tableName, typeName, default)`** — matches on lowercase type name and optional default
- **`ColName(tableName, columnName)`** — matches on table and column name
- **`AsBoolean(b)`**, **`AsInt(i)`**, **`AsLong(l)`**, **`AsDouble(d)`** — parse default value strings

## Scalameta DSL

Type and term construction uses `ScalaMetaDsl` interpolators and extension methods:

```scala
import slick.additions.codegen.ScalaMetaDsl._

typ"MyType"                          // Type.Name("MyType")
term"myTerm"                         // Term.Name("myTerm")
typ"Option".typeApply(typ"String")   // Option[String]
term"Some".termApply(term"x")        // Some(x)
term"pkg".termSelect("sub")         // pkg.sub
term"pkg".typeSelect(typ"MyType")   // pkg.MyType
```

## API Reference

Full Scaladoc is available at [javadoc.io](https://javadoc.io/doc/io.github.nafg/slick-additions-codegen_2.13/latest).
