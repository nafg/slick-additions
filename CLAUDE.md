# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A Scala library providing type-safe entity management and code generation for Slick (database access library). Key innovation: a sealed ADT hierarchy (`Entity.scala`) that distinguishes saved/unsaved/modified state at compile time, plus `Lookup` types for type-safe foreign key references.

## Build Commands

```bash
sbt compile                    # Compile all modules
sbt test                       # Run all tests
sbt +compile                   # Cross-compile for all Scala versions (2.12, 2.13, 3.3)
sbt +test                      # Cross-test all Scala versions
sbt +testQuick                 # Cross-test only tests affected by recent changes
sbt "project root" test        # Test the root module only (H2 in-memory DB tests)
sbt +publishLocal              # Cross-publish all modules to local Ivy cache (for downstream testing)
```

### Codegen Verification (as CI does it)

```bash
sbt slick-additions-codegen/test  # Runs codegen and compares against golden files
sbt test-codegen/compile          # Compiles the generated code to verify it's valid
```

CI also runs `git diff --exit-code` after codegen to ensure generated output hasn't changed without updating golden files. If you modify codegen logic, you must update the golden files in `slick-additions-codegen/src/test/resources/`.

## Project Structure

| Module | Description | Scala Versions |
|--------|-------------|----------------|
| `slick-additions-entity` | Pure ADT hierarchy (Entity, Lookup, etc.) — zero dependencies, cross-compiles to JS | 2.12, 2.13, 3.3 |
| `slick-additions` (root) | Core Slick integration: `AdditionsProfile`, `KeyedTable`, `EntityTable`, `AutoName` | 2.12, 2.13, 3.3 |
| `slick-additions-codegen` | Code generator using Scalameta + Scalafmt — generates table/model code from DB schema | 2.12, 2.13, 3.3 |
| `slick-additions-testcontainers` | PostgreSQL TestContainers convenience wrapper for Slick | 2.12, 2.13, 3.3 |
| `test-codegen` | Pseudo-module that compiles codegen golden files to verify they're valid | 2.12 only |

## Architecture

### Entity Type Hierarchy (`slick-additions-entity`)

```
EntityRef[K, A]           — common base
├── Lookup[K, A]          — has key, may have value
│   └── EntityKey[K, A]   — key only (references)
└── Entity[K, A]          — has value, may have key
    ├── KeylessEntity[K, A]  — value only (unsaved)
    └── KeyedEntity[K, A]    — has both key and value
        ├── SavedEntity[K, A]    — persisted state
        └── ModifiedEntity[K, A] — dirty state
```

### AdditionsProfile (`slick-additions` root)

`AdditionsProfile` extends `JdbcProfile` and provides:
- `KeyedTable[K, A]` — table with separate key column
- `EntityTable[K, V]` — table that maps rows to `Entity[K, V]`
- `EntTableQuery[K, V, T]` — TableQuery with `insert`/`update`/`save`/`delete` returning DBIO actions
- `AutoName` / `AutoNameSnakify` — infers column/FK/index names from Scala identifiers via `sourcecode`
- `EntityTableModule[K, V]` — abstract module pattern that bundles table + query definition
- Implicit `BaseColumnType` for `Lookup` (maps to key column in DB)

### Code Generation (`slick-additions-codegen`)

Pluggable architecture using traits:
- `GenerationRules` — base class for customizing what gets generated (filter tables, map types, configure columns)
- `EntityGenerationRules` — wraps FK fields in `Lookup` type
- `PostgresGenerationRules` — maps Postgres types (TIMESTAMP→Instant, TIME→LocalTime, arrays→List)
- `NamingRules` — snake_case DB names → camelCase Scala identifiers
- `FileCodeGenerator` — generates formatted code files via Scalameta AST + Scalafmt
- Golden file tests compare generated output against expected files in `src/test/resources/`

## Conventions

- Package: `slick.additions` / `slick.additions.entity` / `slick.additions.codegen`
- Type parameters: `K` for key type, `V` or `A` for value type
- `NameStyle.Snakify` converts camelCase to snake_case for DB column names
- `.scalafmt.conf`: maxColumn 120, scala213source3 dialect
- Codegen module cross-compiles for all supported Scala versions
- Tests use ScalaTest `AnyFunSuite` with H2 in-memory databases
