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
      def query(implicit shape: Shape[KeyedTable.this.type, A, KeyedTable.this.type]) = {
        import simple._
        Query(KeyedTable.this: KeyedTable.this.type).filter{ t => columnExtensionMethods(t.key) is key }
      }
      def obj(implicit shape: Shape[KeyedTable.this.type, A, KeyedTable.this.type], session: scala.slick.session.Session): Option[A] = {
        import simple._
        query.firstOption
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
