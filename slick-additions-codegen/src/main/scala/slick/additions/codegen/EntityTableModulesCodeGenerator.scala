package slick.additions.codegen

import scala.meta._

import slick.jdbc.JdbcProfile


/** Uses `slick-additions` `EntityTableModule` to represent tables. Generates a custom profile object that mixes in
  * `AdditionsProfile` with an `api` member that mixes in `AdditionsApi`.
  *
  * Models should be generated with [[KeylessModelsCodeGenerator]].
  *
  * Generated code requires `slick-additions`.
  */
class EntityTableModulesCodeGenerator extends TablesCodeGenerator {
  override protected def profileImport(slickProfileClass: Class[_ <: JdbcProfile]) = {
    val profileName = Init(toTypeRef(slickProfileClass.getName.stripSuffix("$")), Name.Anonymous(), Seq())

    q"""
      import slick.additions.AdditionsProfile
      import slick.lifted.MappedProjection

      trait SlickProfile extends $profileName with AdditionsProfile {
        object myApi extends JdbcAPI with AdditionsApi
        override val api: myApi.type = myApi
      }
      object SlickProfile extends SlickProfile

      import SlickProfile.api._
      """.stats
  }

  override def tableStats = {
    case tableConfig @ TableConfig(tableMetadata, tableClassName, modelClassName, columns) =>
      columns.partition(c => tableMetadata.primaryKeys.exists(_.column == c.column.name)) match {
        case (Seq(pk), otherCols) =>
          val fields  = otherCols.map(columnField)
          val mapping = mkMapping(modelClassName, q"mapping", otherCols)
          val keyType = pk.scalaType
          List(
            q"""
              object ${Term.Name(tableClassName)}
                  extends EntityTableModule[$keyType, ${Type.Name(modelClassName)}](${tableMetadata.table.name.name}) {
                class Row(tag: Tag) extends BaseEntRow(tag) {
                  override def keyColumnName = ${pk.column.name}
                  ..$fields
                  $mapping
                }
              }
              """
          )

        case _ =>
          super.tableStats(tableConfig)
      }
  }
}
