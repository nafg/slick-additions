package scala.slick
package additions

import lifted._
import driver._
import scala.reflect.runtime.currentMirror

sealed trait Entity[K, A] {
  def value: A

  def isSaved: Boolean

  def transform(f: A => A): Entity[K, A]
}
case class KeylessEntity[K, A](value: A) extends Entity[K, A] {
  final def isSaved = false

  def transform(f: A => A): KeylessEntity[K, A] = KeylessEntity[K, A](f(value))
}
sealed trait KeyedEntity[K, A] extends Entity[K, A] {
  def key: K

  def transform(f: A => A): ModifiedEntity[K, A] = ModifiedEntity[K, A](key, f(value))
}
case class SavedEntity[K, A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = true
}
case class ModifiedEntity[K, A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = false
}


trait KeyedTableComponent extends BasicDriver {
  abstract class KeyedTable[K : BaseTypeMapper, A](tableName: String) extends Table[A](tableName) {
    def keyColumnName = "id"
    def keyColumnOptions = List(O.PrimaryKey, O.NotNull, O.AutoInc)
    def key = column[K](keyColumnName, keyColumnOptions: _*)

    def lookup: Column[Lookup] = column[Lookup](keyColumnName, keyColumnOptions: _*)

    case class Lookup(key: K) extends additions.Lookup[Option[A], simple.Session] {
      import simple._
      def query: Query[simple.KeyedTable[K, A], A] = {
        Query(KeyedTable.this).filter(_.key is key)
      }
      def compute(implicit session: simple.Session): Option[A] = query.firstOption
    }

    case class OneToMany[B, TB <: simple.Table[B]](key: Option[K], otherTable: TB)(column: TB => Column[Lookup]) extends additions.Lookup[Seq[B], simple.Session] {
      import simple._
      def query: Query[TB, B] = for {
        t <- KeyedTable.this if key map (t.key is _) getOrElse ConstColumn.TRUE
        o <- otherTable      if column(o) is lookup
      } yield o
      def compute(implicit session: simple.Session): Seq[B] = query.list
    }

    implicit def lookupMapper: BaseTypeMapper[Lookup] =
      MappedTypeMapper.base[Lookup, K](_.key, Lookup(_))
  }

  abstract class EntityTable[K : BaseTypeMapper, A](tableName: String) extends KeyedTable[K, KeyedEntity[K, A]](tableName) {
    type Ent      = Entity[K, A]
    type KeyedEnt = KeyedEntity[K, A]

    private var _mapping = Option.empty[(ColumnBase[A], (ColumnBase[KeyedEntity[K, A]]))]
    def mapping = _mapping
    def mapping_=(m: (ColumnBase[A], (ColumnBase[KeyedEntity[K, A]]))) =
      _mapping = Some(m)
    def mapping_=(c: Column[A]) =
      _mapping = Some((c, key ~ c <> (SavedEntity(_, _), ke => Some((ke.key, ke.value)))))
    def mapping_=[T1,T2,T3](p: Projection3[T1,T2,T3])(implicit ev: A =:= (T1, T2, T3)) =
      _mapping = Some(p <-> (_ => Function.untupled(x => x.asInstanceOf[A]), x => Some(x)))

    def forInsert: ColumnBase[A] = _mapping map (_._1) getOrElse sys.error("No entity mapping provided")

    def * = _mapping map (_._2) getOrElse sys.error("No entity mapping provided")

    def insert(v: A)(implicit session: simple.Session): SavedEntity[K, A] = insert(KeylessEntity[K, A](v))
    def insert(e: Entity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      e match {
        case ke: KeylessEntity[K, A] => SavedEntity(forInsert returning key insert ke.value, ke.value)
        case ke: KeyedEntity[K, A]   => SavedEntity(* returning key insert ke, ke.value)
      }
    }
    def update(ke: KeyedEntity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      Query(*) update ke
      SavedEntity(ke.key, ke.value)
    }
    def save(e: Entity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      e match {
        case ke: KeylessEntity[K, A] => insert(ke)
        case ke: KeyedEntity[K, A]   => update(ke)
      }
    }

    def delete(ke: KeyedEntity[K, A])(implicit session: simple.Session) = {
      import simple._
      Query(this).filter(_.key is ke.key).delete
    }

    //TODO EntityMapping{6..22}
    trait EntityMapping[Ap, Unap] {
      type _Ap = Ap
      type _Unap = Unap
      def <->(ap: Option[K] => Ap, unap: Unap): (ColumnBase[A], ColumnBase[KeyedEntity[K, A]])
      def <->(ap: Ap, unap: Unap): (ColumnBase[A], ColumnBase[KeyedEntity[K, A]]) = <->(_ => ap, unap)
    }
    implicit class EntityMapping2[T1, T2](val p: Projection2[T1, T2]) extends EntityMapping[(T1, T2) => A, A => Option[(T1, T2)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = (p <> (ap(None), unap)) -> (key ~ p).<>[KeyedEntity[K, A]](
        (t: (K, T1, T2)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3)),
        (ke: KeyedEntity[K, A]) => unap(ke.value) map (t => (ke.key, t._1, t._2))
      )
    }
    implicit class EntityMapping3[T1, T2, T3](val p: Projection3[T1, T2, T3]) extends EntityMapping[(T1, T2, T3) => A, A => Option[(T1, T2, T3)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = (p <> (ap(None), unap)) -> (key ~ p).<>[KeyedEntity[K, A]](
        (t: (K, T1, T2, T3)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4)),
        (ke: KeyedEntity[K, A]) => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3))
      )
    }
    implicit class EntityMapping4[T1, T2, T3, T4](val p: Projection4[T1, T2, T3, T4]) extends EntityMapping[(T1, T2, T3, T4) => A, A => Option[(T1, T2, T3, T4)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = (p <> (ap(None), unap)) -> (key ~ p).<>[KeyedEntity[K, A]](
        (t: (K, T1, T2, T3, T4)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5)),
        (ke: KeyedEntity[K, A]) => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4))
      )
    }
    implicit class EntityMapping5[T1, T2, T3, T4, T5](val p: Projection5[T1, T2, T3, T4, T5]) extends EntityMapping[(T1, T2, T3, T4, T5) => A, A => Option[(T1, T2, T3, T4, T5)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = (p <> (ap(None), unap)) -> (key ~ p).<>[KeyedEntity[K, A]](
        (t: (K, T1, T2, T3, T4, T5)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6)),
        (ke: KeyedEntity[K, A]) => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5))
      )
    }
  }

  trait SimpleQL extends super.SimpleQL {
    type KeyedTable[K, A] = KeyedTableComponent.this.KeyedTable[K, A]
    type EntityTable[K, A] = KeyedTableComponent.this.EntityTable[K, A]
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

/**
 * A Lookup is a wrapper for a value that is lazily loaded.
 * Once it is loaded, its entity is cached and
 * does not need to be computed again.
 * It's different than other lazy computation wrappers
 * in that its computation has access to a typed parameter.
 */
abstract class Lookup[A, Param] {
  /**
   * Force the computation. Does not cache its result.
   * @return the result of the computation
   */
  def compute(implicit param: Param): A

  @volatile protected var _cached = Option.empty[A]
  /**
   * @return the possibly cached value
   */
  def cached: Option[A] = _cached

  /**
   * Directly set the cache
   * @return `this`
   * @example {{{ myLookup ()= myValue }}}
   */
  def update(a: A): this.type = {
    _cached = Some(a)
    this
  }

  /**
   * Clear the cache
   */
  def clear = _cached = None
  /**
   * Return the value.
   * If it hasn't been computed yet, compute it and cache the result.
   * @return the cached value
   * @example {{{ myLookup() }}}
   */
  def apply()(implicit param: Param): A = {
    _cached = cached orElse Some(compute)
    _cached.get
  }
}
