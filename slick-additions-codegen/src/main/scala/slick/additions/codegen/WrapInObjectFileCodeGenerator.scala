package slick.additions.codegen

import scala.meta.Term

import slick.additions.codegen.ScalaMetaDsl.defObject


/** Wraps all generated statements inside a top-level object. The object name defaults to
  * [[FileCodeGenerator.filename]].
  */
//noinspection ScalaUnusedSymbol
trait WrapInObjectFileCodeGenerator extends FileCodeGenerator {
  // noinspection ScalaWeakerAccess
  protected def container = filename

  override protected def fileStatements(objectConfigs: List[generationRules.ObjectConfigType]) =
    List(
      defObject(Term.Name(container))(
        super.fileStatements(objectConfigs)
      )
    )
}
