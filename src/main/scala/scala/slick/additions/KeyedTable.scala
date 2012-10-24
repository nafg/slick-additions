package scala.slick
package additions

import lifted._
import driver._
import scala.reflect.runtime.currentMirror

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

  trait SimpleQL extends super.SimpleQL {
    type KeyedTable[A, K] = KeyedTableComponent.this.KeyedTable[A, K]
  }
  override val simple: SimpleQL = new SimpleQL {}
}

trait NamingDriver extends KeyedTableComponent {
  abstract class KeyedTable[A, K](tableName: String)(implicit btm: BaseTypeMapper[K]) extends super.KeyedTable[A, K](tableName) {
    def this()(implicit btm: BaseTypeMapper[K]) =
     this(currentMirror.classSymbol(Class.forName(Thread.currentThread.getStackTrace()(2).getClassName)).name.decoded)(btm)

    def column[C](options: ColumnOption[C]*)(implicit tm: TypeMapper[C]): Column[C] =
      column[C](scala.reflect.NameTransformer.decode(Thread.currentThread.getStackTrace()(2).getMethodName), options: _*)
  }
  trait SimpleQL extends super.SimpleQL {
    //override type KeyedTable[A, K] = NamingDriver.this.KeyedTable[A, K]
  }
  override val simple: SimpleQL = new SimpleQL {}
}
