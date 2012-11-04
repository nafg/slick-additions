package scala.slick
package additions

import lifted._
import driver._
import scala.reflect.runtime.currentMirror

sealed trait Entity[K, A] {
  def entity: A

  def isSaved: Boolean
}
case class KeylessEntity[A](entity: A) extends Entity[Nothing, A] {
  final def isSaved = false
}
sealed trait KeyedEntity[K, A] extends Entity[K, A] {
  def key: K
}
case class SavedEntity[K, A](key: K, entity: A) extends KeyedEntity[K, A] {
  final def isSaved = true
}
case class ModifiedEntity[K, A](key: K, entity: A) extends KeyedEntity[K, A] {
  final def isSaved = false
}


trait KeyedTableComponent extends BasicDriver {
  abstract class KeyedTable[K : BaseTypeMapper, A](tableName: String) extends Table[KeyedEntity[K, A]](tableName) {
    def keyColumnName = "id"
    def keyColumnOptions = List(O.PrimaryKey, O.NotNull, O.AutoInc)
    def key = column[K](keyColumnName, keyColumnOptions: _*)

    def lookup: Column[Lookup] = column[Lookup](keyColumnName, keyColumnOptions: _*)

    def forInsert: ColumnBase[A]

    def insert(e: Entity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      e match {
        case ke: KeylessEntity[A]  => SavedEntity(forInsert returning key insert ke.entity, ke.entity)
        case ke: KeyedEntity[K, A] => SavedEntity(* returning key insert ke, ke.entity)
      }
    }

    def save(e: Entity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      e match {
        case ke: KeylessEntity[A] => SavedEntity(forInsert returning key insert ke.entity, ke.entity)
        case ke: KeyedEntity[K, A] =>
          Query(*) update ke
          SavedEntity(ke.key, ke.entity)
      }
    }

    def delete(ke: KeyedEntity[K, A])(implicit session: simple.Session) = {
      import simple._
      Query(this).filter(_.key is ke.key).delete
    }

    class Lookup(key: K) extends KeyedTableComponent.this.Lookup[K, A, this.type](this, key)
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
  case class Lookup[K : BaseTypeMapper, A, T <: KeyedTable[K, A]](table: T, key: K) {
    def query: Query[T, KeyedEntity[K, A]] = {
      import simple._
      Query(table).filter(_.key is key)
    }
    @volatile private[KeyedTableComponent] var _obj = Option.empty[A]
    def cached = _obj
    def obj(implicit session: scala.slick.session.Session): Option[A] = {
      import simple._
      if(_obj.isEmpty)
        _obj = query.firstOption.map(_.entity)
      _obj
    }
  }

  trait SimpleQL extends super.SimpleQL {
    type KeyedTable[K, A] = KeyedTableComponent.this.KeyedTable[K, A]
  }
  override val simple: SimpleQL = new SimpleQL {}
}

trait NamingDriver extends KeyedTableComponent {
  abstract class KeyedTable[K, A](tableName: String)(implicit btm: BaseTypeMapper[K]) extends super.KeyedTable[K, A](tableName) {
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
