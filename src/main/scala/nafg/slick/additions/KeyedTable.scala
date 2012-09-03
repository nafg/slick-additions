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
    def lookup = column[Lookup](keyColumnName, keyColumnOptions: _*)

    class Lookup(key: K) extends KeyedTableComponent.this.Lookup[A, K, this.type](this, key)
    object Lookup {
      def apply(key: K): Lookup = new Lookup(key)
    }
    implicit def lookupMapper: BaseTypeMapper[Lookup] =
      MappedTypeMapper.base[Lookup, K](_.key, Lookup(_))
  }
  case class Lookup[A, K : BaseTypeMapper, T <: KeyedTable[A, K]](table: T, key: K) {
    def query: Query[T, A] = {
      import simple._
      Query(table).filter(_.key is key)
    }
    def obj(implicit session: scala.slick.session.Session): Option[A] = {
      import simple._
      query.firstOption
    }
  }

  override val simple = new SimpleQL {
    type KeyedTable[A, K] = KeyedTableComponent.this.KeyedTable[A, K]
  }
}
