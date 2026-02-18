package slick.additions.codegen

import scala.meta.{Ctor, Defn, Import, Importee, Importer, Mod, Name, Type}

import slick.additions.codegen.ScalaMetaDsl.{
  defObject, defVal, init, scalametaNonMacroInterpolators, scalametaTermExtensionMethods, template, toTypeRef
}
import slick.jdbc.JdbcProfile


/** Uses `slick-additions` `EntityTableModule` to represent tables. Generates a custom profile object that mixes in
  * `AdditionsProfile` with an `api` member that mixes in `AdditionsApi`.
  *
  * Models should be generated with [[EntityModelsObjectCodeGenerator]].
  *
  * Generated code requires `slick-additions`.
  */
//noinspection ScalaUnusedSymbol
trait EntityTableModulesFileCodeGenerator extends SlickFileCodeGenerator with EntityFileCodeGenerator {
  override protected def imports = super.imports ++ List("slick.lifted.MappedProjection")

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

  override def objectCodeGenerator(config: EntityGenerationRules.ObjectConfig) =
    config match {
      case config: EntityGenerationRules.ObjectConfig.EntityTable     =>
        new EntityTableModulesObjectCodeGenerator(config)
      case EntityGenerationRules.ObjectConfig.BasicTable(tableConfig) =>
        new TablesObjectCodeGenerator(tableConfig)
    }
}
