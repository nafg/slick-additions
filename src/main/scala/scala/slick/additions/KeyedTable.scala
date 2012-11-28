package scala.slick
package additions

import lifted._
import driver._
import scala.reflect.runtime.currentMirror

sealed trait Entity[K, +A] {
  def value: A

  def isSaved: Boolean

  def map[B >: A](f: A => B): Entity[K, B]

  def duplicate = new KeylessEntity[K, A](value)
}
class KeylessEntity[K, +A](val value: A) extends Entity[K, A] {
  final def isSaved = false

  override def equals(that: Any) = this eq that.asInstanceOf[AnyRef]

  def map[B >: A](f: A => B): KeylessEntity[K, B] = new KeylessEntity[K, B](f(value))
}
sealed trait KeyedEntity[K, +A] extends Entity[K, A] {
  def key: K

  def map[B >: A](f: A => B): ModifiedEntity[K, B] = ModifiedEntity[K, B](key, f(value))
}
case class SavedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = true
}
case class ModifiedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = false
}


trait KeyedTableComponent extends BasicDriver {
  abstract class KeyedTable[K : BaseTypeMapper, A](tableName: String) extends Table[A](tableName) { keyedTable =>
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


    class OneToMany[E >: B, B, TB <: simple.Table[B]](
      otherTable: TB with simple.Table[B], thisLookup: Option[Lookup]
    )(
      column: TB => Column[Lookup], setLookup: Lookup => E => E
    ) extends additions.SeqLookup[E, simple.Session] with DiffSeq[E, OneToMany[E, B, TB]] {
      import simple._

      def query: Query[TB, B] =
        Query(KeyedTable.this)
          .filter(t => thisLookup map (t.key is _.key) getOrElse ConstColumn(false))
          .flatMap{ t =>
            Query(otherTable).filter(column(_) is t.asInstanceOf[KeyedTable.this.type].lookup)
          }

      def compute(implicit session: simple.Session): Seq[E] = query.list

      protected def copy(items: Seq[Handle[E]]) = new OneToMany[E, B, TB](otherTable, thisLookup)(column, setLookup) {
        override val initialItems = OneToMany.this.initialItems
        override val currentItems = items
      }

      def withLookup(lookup: Lookup) = (new OneToMany[E, B, TB](otherTable, Some(lookup))(column, setLookup) {
        override val initialItems = OneToMany.this.initialItems
        override val currentItems = OneToMany.this.currentItems
      }) map setLookup(lookup)

      def save[BK, ETB <: simple.EntityTable[BK, B]](implicit session: simple.Session, ev1: TB <:< ETB, ev2: B <:< ETB#KEnt) = ??? // TODO
    }

    def OneToMany[B, TB <: simple.Table[B]](
      otherTable: TB with simple.Table[B], lookup: Option[Lookup]
    )(
      column: TB => Column[Lookup], setLookup: Lookup => B => B, initial: Seq[B] = null
    ) = new OneToMany[B, B, TB](otherTable, lookup)(column, setLookup) {
      _cached = Option(initial)
    }

    def OneToManyEnt[K, A, TB <: simple.EntityTable[K, A]](
      otherTable: TB with simple.EntityTable[K, A], lookup: Option[Lookup]
    )(
      column: TB => Column[Lookup], setLookup: Lookup => A => A, initial: Seq[TB#Ent] = null
    ) = new OneToMany[TB#Ent, TB#KEnt, TB](otherTable, lookup)(column, l => _.map(setLookup(l))) {
      _cached = Option(initial)
    }

    type OneToManyEnt[K, A, TB <: simple.EntityTable[K, A]] = OneToMany[TB#Ent, TB#KEnt, TB]

    implicit def lookupMapper: BaseTypeMapper[Lookup] =
      MappedTypeMapper.base[Lookup, K](_.key, Lookup(_))
  }

  abstract class EntityTable[K : BaseTypeMapper, A](tableName: String) extends KeyedTable[K, KeyedEntity[K, A]](tableName) {
    type Key = K
    type Value = A
    type Ent   = Entity[K, A]
    type KEnt  = KeyedEntity[K, A]

    def Ent(a: A) = new KeylessEntity[K, A](a)

    case class Mapping(forInsert: ColumnBase[A], * : ColumnBase[KEnt])
    object Mapping {
      implicit def fromColumn(c: Column[A]) =
        Mapping(
          c,
          key ~ c <> (
            SavedEntity(_, _),
            { case ke: KEnt => Some((ke.key, ke.value)) }
          )
        )
      implicit def fromProjection3[T1,T2,T3](p: Projection3[T1,T2,T3])(implicit ev: A =:= (T1, T2, T3)) =
        p <-> (_ => Function.untupled(x => x.asInstanceOf[A]), x => Some(x))
    }

    def mapping: Mapping

    def forInsert: ColumnBase[A] = mapping.forInsert

    def * = mapping.*

    def insert(v: A)(implicit session: simple.Session): SavedEntity[K, A] = insert(Ent(v))
    def insert(e: Entity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      e match {
        case ke: KEnt => SavedEntity(* returning key insert ke, ke.value)
        case ke: Ent  => SavedEntity(forInsert returning key insert ke.value, ke.value)
      }
    }
    def update(ke: KEnt)(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      Query(*) update ke
      SavedEntity(ke.key, ke.value)
    }
    def save(e: Entity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      e match {
        case ke: KEnt => update(ke)
        case ke: Ent  => insert(ke)
      }
    }

    def delete(ke: KEnt)(implicit session: simple.Session) = {
      import simple._
      Query(this).filter(_.key is ke.key).delete
    }

    //TODO EntityMapping{6..22}
    trait EntityMapping[Ap, Unap] {
      type _Ap = Ap
      type _Unap = Unap
      def <->(ap: Option[K] => Ap, unap: Unap): Mapping
      def <->(ap: Ap, unap: Unap): Mapping = <->(_ => ap, unap)
    }
    implicit class EntityMapping2[T1, T2](val p: Projection2[T1, T2]) extends EntityMapping[(T1, T2) => A, A => Option[(T1, T2)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2)) }
        )
      )
    }
    implicit class EntityMapping3[T1, T2, T3](val p: Projection3[T1, T2, T3]) extends EntityMapping[(T1, T2, T3) => A, A => Option[(T1, T2, T3)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3)) }
        )
      )
    }
    implicit class EntityMapping4[T1, T2, T3, T4](val p: Projection4[T1, T2, T3, T4]) extends EntityMapping[(T1, T2, T3, T4) => A, A => Option[(T1, T2, T3, T4)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4)) }
        )
      )
    }
    implicit class EntityMapping5[T1, T2, T3, T4, T5](val p: Projection5[T1, T2, T3, T4, T5]) extends EntityMapping[(T1, T2, T3, T4, T5) => A, A => Option[(T1, T2, T3, T4, T5)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5)) }
        )
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
