package slick.additions.codegen

import scala.meta.*

import slick.additions.codegen.ScalaMetaDsl.*


class EntityTableModulesObjectCodeGenerator(config: EntityGenerationRules.ObjectConfig.EntityTable)
    extends SlickObjectCodeGenerator {
  override def mappingType(rowClassType: Type.Name) = typ"MappedProjection".typeApply(rowClassType)

  // noinspection ScalaWeakerAccess
  protected def defKeyColumnName(pk: ColumnConfig): List[Stat] =
    if (pk.column.name == "id")
      Nil
    else
      List(
        defDef("keyColumnName", modifiers = List(Mod.Override()))()(
          Lit.String(pk.column.name)
        )
      )

  protected def tableModuleBaseType(pk: ColumnConfig): Type =
    typ"EntityTableModule".typeApply(pk.scalaType, typ"${config.modelClassName}")

  // noinspection ScalaWeakerAccess
  protected def tableModuleBases(pk: ColumnConfig) =
    Seq(init(tableModuleBaseType(pk), Seq(Seq(Lit.String(pk.column.table.name)))))

  // noinspection ScalaWeakerAccess
  protected def tableRowBase = typ"BaseEntRow"

  // noinspection ScalaWeakerAccess
  protected def tableMappingName = "mapping"

  override def statements =
    List(
      defObject(Term.Name(config.tableClassName), tableModuleBases(config.keyColumn)*)(
        List(
          defClass(
            "Row",
            params = List(termParam(term"tag", typ"Tag")),
            inits = List(init(tableRowBase, Seq(Seq(term"tag"))))
          )(
            defKeyColumnName(config.keyColumn) ++
              config.mappedColumns.map(columnField) :+
              mkMapping(config.modelClassName, tableMappingName, config.mappedColumns)
          )
        )
      )
    )
}
