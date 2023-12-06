package slick.additions.codegen

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.meta._

import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MQName


/** Code generator for standard Slick table definitions. The generated code has no dependency on slick-additions.
  *
  * Tables that have more than 22 fields are mapped by simply nesting tuples so that no single tuple has more than 22
  * elements.
  */
class TablesCodeGenerator extends BaseCodeGenerator {
  // noinspection ScalaWeakerAccess
  def isDefaultSchema(schema: String) = schema == "public"

  def mkMapping(rowClassName: String, mappingName: Term.Name, columns: List[ColumnConfig]) = {
    val companion                   = Term.Name(rowClassName)
    val rowClassType                = Type.Name(rowClassName)
    val terms                       = columns.map(_.tableFieldTerm)
    val numCols                     = columns.length
    val (tuple, factory, extractor) =
      if (numCols == 1)
        (terms.head, q"$companion.apply", q"$companion.unapply")
      else if (numCols <= 22)
        (Term.Tuple(terms), q"($companion.apply _).tupled", q"$companion.unapply")
      else {
        @tailrec
        def group22[A](values: List[A])(group: List[A] => A): A =
          values match {
            case List(one) => one
            case _         =>
              val (first, second) = values.splitAt(22)
              group22(group(first) +: second)(group)
          }

        val fac =
          Term.PartialFunction(
            List(
              p"""
                case ${group22[Pat](terms.map(Pat.Var(_)))(Pat.Tuple(_))} =>
                  $companion(..$terms)
              """
            )
          )

        val extractor =
          q"(rec: ${Type.Name(rowClassName)}) => Some(${group22[Term](terms.map(t => q"rec.$t"))(Term.Tuple(_))})"

        (group22[Term](terms)(Term.Tuple(_)), fac, extractor)
      }

    q"def $mappingName: MappedProjection[$rowClassType, ?] = $tuple.<>({$factory}, $extractor)"
  }

  def columnField: ColumnConfig => Stat = {
    case ColumnConfig(column, tableFieldName, _, scalaType, _) =>
      q"""
         val ${Pat.Var(tableFieldName)} = column[$scalaType](${column.name})
         """
  }

  def tableStats: TableConfig => List[Stat] = {
    case TableConfig(tableMetadata, tableClassName, modelClassName, columns) =>
      val fields  = columns.map(columnField)
      val mapping = mkMapping(modelClassName, q"*", columns)
      val params  = tableMetadata.table.name match {
        case MQName(None, Some(schema), name) if !isDefaultSchema(schema) => List(q"Some($schema)", Lit.String(name))
        case MQName(None, _, name)                                        => List(Lit.String(name))
        case MQName(Some(_), _, _)                                        => sys.error("catalog not supported")
      }
      List(
        q"""
          class ${Type.Name(tableClassName)}(_tableTag: Tag)
            extends Table[${Type.Name(modelClassName)}](_tableTag, ..$params) {
              ..$fields
              $mapping
            }
            """,
        q"""
          lazy val ${Pat.Var(Term.Name(tableClassName))} = TableQuery[${Type.Name(tableClassName)}]
          """
      )
  }

  protected def profileImport(slickProfileClass: Class[_ <: JdbcProfile]): List[Stat] = {
    val profileName = toTermRef(slickProfileClass.getName.stripSuffix("$"))
    List(q"import $profileName.api._")
  }

  override def codeString(
    rules: GenerationRules,
    slickProfileClass: Class[_ <: JdbcProfile]
  )(implicit executionContext: ExecutionContext
  ) =
    rules.tableConfigs(slickProfileClass).map { tableConfigs =>
      q"""
        package ${toTermRef(rules.packageName)} {
          ..${profileImport(slickProfileClass)}

          ..${imports(rules.extraImports)}

          object ${Term.Name(rules.container)} {
            ..${tableConfigs.flatMap(tableStats)}
          }
        }
       """.syntax
    }
}
