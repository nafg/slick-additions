package slick.additions.codegen

import scala.meta.Term

import slick.additions.codegen.ScalaMetaDsl.defObject


//noinspection ScalaUnusedSymbol
trait WrapInObjectFileCodeGenerator extends FileCodeGenerator {
  // noinspection ScalaWeakerAccess
  protected def container = filename

  override protected def fileStatements(tableConfigs: List[TableConfig]) =
    List(
      defObject(Term.Name(container))(
        super.fileStatements(tableConfigs)
      )
    )
}
