package slick.additions.codegen

import scala.meta.Term

import slick.additions.codegen.ScalaMetaDsl.defObject


//noinspection ScalaUnusedSymbol
trait WrapInObjectCodeGenerator extends BaseCodeGenerator {
  // noinspection ScalaWeakerAccess
  protected def container = filename

  override protected def fileStats(tableConfigs: List[TableConfig]) =
    List(
      defObject(Term.Name(container))(
        super.fileStats(tableConfigs)
      )
    )
}
