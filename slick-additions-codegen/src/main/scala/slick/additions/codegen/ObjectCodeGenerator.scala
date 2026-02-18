package slick.additions.codegen

import scala.meta.Stat


/** Per-database-object code generator that produces Scalameta statements for one database object.
  *
  * A [[FileCodeGenerator]] creates one `ObjectCodeGenerator` per table (via `objectCodeGenerator`) and collects all
  * their statements into a single generated file.
  *
  * @see
  *   [[BaseModelsObjectCodeGenerator]] for model case class generation
  * @see
  *   [[TablesObjectCodeGenerator]] for Slick table definition generation
  */
trait ObjectCodeGenerator {
  def statements: List[Stat]
}
