package slick.additions.codegen

import scala.meta.{Import, Importee, Importer, Stat}

import slick.additions.codegen.ScalaMetaDsl.{scalametaTermExtensionMethods, toTermRef}
import slick.jdbc.JdbcProfile


trait SlickFileCodeGenerator extends FileCodeGenerator {
  protected def profileImport(slickProfileClass: Class[_ <: JdbcProfile]): List[Stat] = {
    val profileName = toTermRef(slickProfileClass.getName.stripSuffix("$"))
    List(
      Import(List(Importer(profileName.termSelect("api"), List(Importee.Wildcard()))))
    )
  }

  override protected def importStatements(extraImports: List[String], slickProfileClass: Class[? <: JdbcProfile])
    : List[Stat] =
    profileImport(slickProfileClass) ++
      super.importStatements(extraImports, slickProfileClass)
}
