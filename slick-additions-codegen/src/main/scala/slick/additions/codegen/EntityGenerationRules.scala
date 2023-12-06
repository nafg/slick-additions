package slick.additions.codegen

import scala.meta._


/** Uses `slick-additions-entity` `Lookup` for foreign key fields.
  *
  * Generated code requires `slick-additions-entity`.
  */
trait EntityGenerationRules extends GenerationRules {
  override def extraImports                                                                 = "slick.additions.entity.Lookup" :: super.extraImports
  override def baseColumnType(currentTableMetadata: TableMetadata, all: Seq[TableMetadata]) =
    Function.unlift { column =>
      super.baseColumnType(currentTableMetadata, all).lift(column).map { typ =>
        currentTableMetadata.foreignKeys.find(_.fkColumn == column.name) match {
          case None     => typ
          case Some(fk) => t"Lookup[$typ, ${Type.Name(modelClassName(fk.pkTable))}]"
        }
      }
    }
}
