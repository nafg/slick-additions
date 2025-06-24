package slick.additions.codegen

import scala.meta._

import slick.additions.codegen.ScalaMetaDsl._
import slick.jdbc.JdbcProfile


/** Uses `slick-additions` `EntityTableModule` to represent tables. Generates a custom profile object that mixes in
  * `AdditionsProfile` with an `api` member that mixes in `AdditionsApi`.
  *
  * Models should be generated with [[KeylessModelsObjectCodeGenerator]].
  *
  * Generated code requires `slick-additions`.
  */
//noinspection ScalaUnusedSymbol
trait EntityTableModulesFileCodeGenerator   extends TablesFileCodeGenerator   {
  override protected def profileImport(slickProfileClass: Class[_ <: JdbcProfile]) =
    List(
      Import(
        List(
          Importer(
            term"slick".termSelect(term"additions"),
            List(Importee.Name(Name("AdditionsProfile")))
          )
        )
      ),
      Import(
        List(
          Importer(
            term"slick".termSelect(term"lifted"),
            List(Importee.Name(Name("MappedProjection")))
          )
        )
      ),
      Defn.Trait(
        mods = Nil,
        name = typ"SlickProfile",
        tparamClause = Type.ParamClause(Nil),
        ctor = Ctor.Primary(Nil, Name.Anonymous(), Seq()),
        templ =
          template(
            init(toTypeRef(slickProfileClass.getName.stripSuffix("$"))),
            init(typ"AdditionsProfile")
          )(
            List(
              defObject(term"myApi", init(typ"JdbcAPI"), init(typ"AdditionsApi"))(),
              defVal(term"api", declaredType = Some(Type.Singleton(term"myApi")), modifiers = List(Mod.Override()))(
                term"myApi"
              )
            )
          )
      ),
      defObject(term"SlickProfile", init(typ"SlickProfile"))(),
      Import(List(Importer(term"SlickProfile".termSelect(term"api"), List(Importee.Wildcard()))))
    )

  override def objectCodeGenerator(tableConfig: TableConfig): EntityTableModulesObjectCodeGenerator =
    new TablesObjectCodeGenerator(tableConfig) with EntityTableModulesObjectCodeGenerator
}
trait EntityTableModulesObjectCodeGenerator extends TablesObjectCodeGenerator {
  override def mappingType(rowClassType: Type.Name) =
    term"slick".termSelect("lifted").typeSelect(typ"MappedProjection")
      .typeApply(rowClassType)

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

  // noinspection ScalaWeakerAccess
  protected def partitionPrimaryKey: (List[ColumnConfig], List[ColumnConfig]) =
    tableConfig.columns.partition(c => tableConfig.tableMetadata.primaryKeys.exists(_.column == c.column.name))

  override def statements =
    tableConfig match {
      case TableConfig(tableMetadata, tableClassName, modelClassName, _) =>
        partitionPrimaryKey match {
          case (Seq(pk), otherCols) =>
            val fields  = otherCols.map(columnField)
            val mapping = mkMapping(modelClassName, "mapping", otherCols)
            val keyType = pk.scalaType

            List(
              defObject(
                Term.Name(tableClassName),
                init(
                  typ"EntityTableModule".typeApply(keyType, typ"$modelClassName"),
                  Seq(Seq(Lit.String(tableMetadata.table.name.name)))
                )
              )(
                List(
                  defClass(
                    "Row",
                    params = List(termParam(term"tag", typ"Tag")),
                    inits = List(init(typ"BaseEntRow", Seq(Seq(term"tag"))))
                  )(
                    defKeyColumnName(pk) ++
                      fields :+
                      mapping
                  )
                )
              )
            )
          case _                    =>
            super.statements
        }
    }
}
