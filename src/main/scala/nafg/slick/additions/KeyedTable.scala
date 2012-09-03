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

    def lookup: Column[Lookup] = column[Lookup](keyColumnName, keyColumnOptions: _*)

    class Lookup(key: K) extends KeyedTableComponent.this.Lookup[A, K, this.type](this, key)
    object Lookup {
      def apply(key: K): Lookup = new Lookup(key)
      /*
       * Create a Lookup and cache the mapper
       */
      def apply(key: K, mapper: A): Lookup = {
        val ret = new Lookup(key)
        ret._obj = Some(mapper)
        ret
      }
      def unapply(lookup: Lookup): Option[K] = Some(lookup.key)
    }
    implicit def lookupMapper: BaseTypeMapper[Lookup] =
      MappedTypeMapper.base[Lookup, K](_.key, Lookup(_))
  }
  /**
   * A Lookup is a wrapper for an entity that is lazily loaded by its key.
   * Once it is loaded, its entity is cached and does not change.
   */
  case class Lookup[A, K : BaseTypeMapper, T <: KeyedTable[A, K]](table: T, key: K) {
    def query: Query[T, A] = {
      import simple._
      Query(table).filter(_.key is key)
    }
    @volatile private[KeyedTableComponent] var _obj = Option.empty[A]
    def cached = _obj
    def obj(implicit session: scala.slick.session.Session): Option[A] = {
      import simple._
      if(_obj.isEmpty)
        _obj = query.firstOption
      _obj
    }
  }

  override val simple = new SimpleQL {
    type KeyedTable[A, K] = KeyedTableComponent.this.KeyedTable[A, K]
  }
}
