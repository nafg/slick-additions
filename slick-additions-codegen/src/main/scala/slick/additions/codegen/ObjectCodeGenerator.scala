package slick.additions.codegen

import scala.meta.Stat


/** Per-table code generator that produces Scalameta statements for one table.
  *
  * A [[FileCodeGenerator]] creates one `ObjectCodeGenerator` per table (via `objectCodeGenerator`) and collects all
  * their statements into a single generated file.
  *
  * @see
  *   [[ModelsObjectCodeGenerator]] for model case class generation
  * @see
  *   [[TablesObjectCodeGenerator]] for Slick table definition generation
  */
trait ObjectCodeGenerator {
  def statements: List[Stat]
}
