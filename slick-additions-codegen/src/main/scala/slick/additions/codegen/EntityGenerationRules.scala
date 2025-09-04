package slick.additions.codegen

import slick.additions.codegen.ScalaMetaDsl.*


/** Uses `slick-additions-entity` `Lookup` for foreign key fields.
  *
  * Generated code requires `slick-additions-entity`.
  */
trait EntityGenerationRules extends GenerationRules {
  override def baseColumnType(currentTableMetadata: TableMetadata, all: Seq[TableMetadata]) =
    Function.unlift { column =>
      super.baseColumnType(currentTableMetadata, all).lift(column).map { keyType =>
        currentTableMetadata.foreignKeys.find(_.fkColumn == column.name) match {
          case None     => keyType
          case Some(fk) =>
            term"slick".termSelect("additions").termSelect("entity")
              .typeSelect(typ"Lookup")
              .typeApply(keyType, typ"${modelClassName(fk.pkTable)}")
        }
      }
    }
}
