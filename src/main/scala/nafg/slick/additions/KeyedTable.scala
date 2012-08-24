package nafg
package slick
package additions


import scala.slick.lifted._
import scala.slick.driver._

trait KeyedTableComponent extends BasicDriver {
  abstract class KeyedTable[A, K : BaseTypeMapper](tableName: String) extends Table[A](tableName) {
    def keyColumnName = "id"
    def keyColumnOptions = List(O.PrimaryKey, O.NotNull, O.AutoInc)
    def key = column[K](keyColumnName, keyColumnOptions: _*)
    @deprecated def id = key
    def lookup = column[Lookup](keyColumnName, keyColumnOptions: _*)

    case class Lookup(key: K) {
      def query[T <: KeyedTable[A, K]](table: T)(implicit shape: Shape[T, A, T]) = {
        import simple._
        Query(table).filter(_.key is key)
      }
      def obj(implicit session: scala.slick.session.Session): Option[A] = {
        import simple._
        query(KeyedTable.this).firstOption
      }
    }
    object Lookup {
      implicit val lookupMapper: BaseTypeMapper[Lookup] = MappedTypeMapper.base[Lookup, K](_.key, Lookup(_))
    }
  }

  override val simple = new SimpleQL {
    type KeyedTable[A, K] = KeyedTableComponent.this.KeyedTable[A, K]
  }
}
