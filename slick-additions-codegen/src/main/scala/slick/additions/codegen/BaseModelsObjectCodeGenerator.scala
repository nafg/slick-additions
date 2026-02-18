package slick.additions.codegen

import scala.meta.{Init, Mod, Stat, Term}

import slick.additions.codegen.ScalaMetaDsl.{defClass, defObject, termParam}


/** Per-table code generator that produces a model case class (and optionally a companion object) for one table.
  *
  * Override the hook methods to customize the generated case class and companion:
  *   - [[modelClassBases]] to add base types to the case class
  *   - [[modelObjectBases]] to generate a companion object with base types
  *
  * @see
  *   [[EntityModelsObjectCodeGenerator]] which filters out primary key columns
  */
class BaseModelsObjectCodeGenerator(protected val modelClassName: String, columnConfigs: List[ColumnConfig])
    extends ObjectCodeGenerator {

  /** Base types for the model case class (the `extends` clause).
    *
    * For example, returning `List(init(typ"MyOps"))` generates `case class MyModel(...) extends MyOps`.
    */
  protected def modelClassBases: List[Init] = Nil

  protected def modelClass =
    defClass(
      modelClassName,
      modifiers = List(Mod.Case()),
      params =
        columnConfigs.map { col =>
          termParam(col.modelFieldTerm, col.scalaType, default = col.scalaDefault)
        },
      inits = modelClassBases
    )()

  /** Base types for the model companion object (the `extends` clause).
    *
    * When non-empty, a companion object is generated. The base traits can contribute members through inheritance.
    */
  protected def modelObjectBases: List[Init] = Nil

  protected def modelObject =
    if (modelObjectBases.isEmpty)
      None
    else
      Some(
        defObject(
          Term.Name(modelClassName),
          modelObjectBases*
        )()
      )

  def statements: List[Stat] = modelClass :: modelObject.toList
}
