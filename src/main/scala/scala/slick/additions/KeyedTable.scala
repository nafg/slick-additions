package scala.slick
package additions

import lifted._
import driver._
import scala.reflect.runtime.currentMirror
import reflect.ClassTag

sealed trait Entity[K, +A] {
  def value: A

  def isSaved: Boolean

  def map[B >: A](f: A => B): Entity[K, B]

  def duplicate = new KeylessEntity[K, A](value)
}
case class KeylessEntity[K, +A](val value: A) extends Entity[K, A] {
  final def isSaved = false

  override def equals(that: Any) = this eq that.asInstanceOf[AnyRef]

  def map[B >: A](f: A => B): KeylessEntity[K, B] = new KeylessEntity[K, B](f(value))

  override def toString = s"KeylessEntity($value)"
}
sealed trait KeyedEntity[K, +A] extends Entity[K, A] {
  def key: K

  def map[B >: A](f: A => B): ModifiedEntity[K, B] = ModifiedEntity[K, B](key, f(value))
}
object KeyedEntity {
  def unapply[K, A](e: Entity[K, A]): Option[(K, A)] = e match {
    case ke: KeyedEntity[K, A] => Some((ke.key, ke.value))
    case _                     => None
  }
}
case class SavedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = true
}
case class ModifiedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = false
}

trait KeyedTableComponent extends BasicDriver {
  abstract class KeyedTableBase[K: BaseTypeMapper, A](tableName: String) extends Table[A](tableName) { keyedTable =>
    def keyColumnName = "id"
    def keyColumnOptions = List(O.PrimaryKey, O.NotNull, O.AutoInc)
    def key = column[K](keyColumnName, keyColumnOptions: _*)
  }

  abstract class KeyedTable[K: BaseTypeMapper, A](tableName: String) extends KeyedTableBase[K, A](tableName) {
    def lookup: Column[Lookup] = column[Lookup](keyColumnName, keyColumnOptions: _*)

    case class Lookup(key: K, precache: A = null.asInstanceOf[A]) extends additions.Lookup[Option[A], simple.Session] {
      cached = Option(precache) map Some.apply

      import simple._
      def query: Query[simple.KeyedTable[K, A], A] = {
        Query(KeyedTable.this).filter(_.key is key)
      }
      def compute(implicit session: simple.Session): Option[A] = query.firstOption

      override def toString = s"${KeyedTable.this.getClass.getSimpleName}.Lookup($key)"
    }

    class OneToMany[E >: B, B, TB <: simple.Table[B]](
      private[KeyedTable] val otherTable: TB with simple.Table[B],
      private[KeyedTable] val thisLookup: Option[Lookup]
    )(
      private[KeyedTable] val column: TB => Column[Lookup],
      private[KeyedTable] val setLookup: Lookup => E => E
    ) extends additions.SeqLookup[E, simple.Session] with DiffSeq[E, OneToMany[E, B, TB]] {

      protected val isCopy = false

      private def thisTable = KeyedTable.this

      // TODO should we compare the elements somehow? Maybe only compare if they're both populated?
      override def equals(o: Any) = o match {
        case that: OneToMany[E, B, TB] =>
            this.thisTable == that.thisTable &&
            this.otherTable == that.otherTable &&
            scala.slick.ast.Node(this.column(this.otherTable)) == scala.slick.ast.Node(that.column(that.otherTable))
        case _ => false
      }

      def copy(items: Seq[Handle[E]]) = new OneToMany[E, B, TB](otherTable, thisLookup)(column, setLookup) {
        override val initialItems = OneToMany.this.initialItems
        override val currentItems = items
        override def apply()(implicit session: simple.Session) = currentItems map (_.value)
        override val isCopy = true
      }

      def withLookup(lookup: Lookup): OneToMany[E, B, TB] = if(isCopy) this map setLookup(lookup) else {
        val f = setLookup(lookup)
        new OneToMany[E, B, TB](otherTable, Some(lookup))(column, setLookup) {
          cached = OneToMany.this.cached
          override def currentItems = OneToMany.this.currentItems map (_ map f)
        }
      }

      import simple._

      def saved[KB, EB, TB2 <: simple.EntityTable[KB, EB]](implicit session: Session, ev: this.type <:< OneToMany[TB2#Ent, TB2#KEnt, TB2]): OneToManyEnt[KB, EB, TB2] = {
        val self: OneToMany[TB2#Ent, TB2#KEnt, TB2] = ev(this)
        val toDelete = initialItems filterNot isRemoved map (_.value) collect {
          case e: KeyedEntity[KB, EB] => e
        }
        val items = currentItems map { h =>
          h.value match {
            case e: SavedEntity[KB, EB] => e
            case e: Entity[KB, EB]      => self.otherTable save e
          }
        }
        toDelete foreach self.otherTable.delete
        new OneToManyEnt[KB, EB, TB2](self.otherTable, thisLookup)(self.column, self.setLookup) {
          cached = Some(items)
        }
      }

      def query: Query[TB, B] =
        Query(KeyedTable.this)
          .filter(t => thisLookup map (t.key is _.key) getOrElse ConstColumn(false))
          .flatMap{ t =>
            Query(otherTable).filter(column(_) is t.asInstanceOf[KeyedTable.this.type].lookup)
          }

      def compute(implicit session: Session): Seq[E] = query.list

      override def toString = s"${KeyedTable.this.getClass.getSimpleName}.OneToMany(${cached map (_.toString) getOrElse currentItems})"
    }

    def OneToMany[B, TB <: simple.Table[B]](
      otherTable: TB with simple.Table[B], lookup: Option[Lookup]
    )(
      column: TB => Column[Lookup], setLookup: Lookup => B => B, initial: Seq[B] = null
    ) = new OneToMany[B, B, TB](otherTable, lookup)(column, setLookup) {
      cached = Option(initial)
    }

    def OneToManyEnt[KB, B, TB <: simple.EntityTable[KB, B]](
      otherTable: TB with simple.EntityTable[KB, B], lookup: Option[Lookup]
    )(
      column: TB => Column[Lookup], setLookup: Lookup => B => B, initial: Seq[TB#Ent] = null
    ) = new OneToMany[TB#Ent, TB#KEnt, TB](otherTable, lookup)(column, l => _.map(setLookup(l))) {
      cached = Option(initial)
    }

    type OneToManyEnt[KB, B, TB <: simple.EntityTable[KB, B]] = OneToMany[TB#Ent, TB#KEnt, TB]

    implicit def lookupMapper: BaseTypeMapper[Lookup] =
      MappedTypeMapper.base[Lookup, K](_.key, Lookup(_))
  }

  trait EntityTableBase[K, A] { this: Table[KeyedEntity[K, A]] =>
    type Key = K
    type Value = A
    type Ent = Entity[K, A]
    type KEnt = KeyedEntity[K, A]

    def Ent(a: A) = new KeylessEntity[K, A](a)
  }

  abstract class EntityTable[K: BaseTypeMapper, A](tableName: String) extends KeyedTable[K, KeyedEntity[K, A]](tableName) with EntityTableBase[K, A] {
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

    trait LookupLens[L] {
      def get: A => L
      def set: L => A => A
      def apply(a: A, f: L => L): A
      def saved(implicit session: simple.Session): L => L
      def setLookup: K => L => L
      def setLookupAndSave(key: K, a: A)(implicit session: simple.Session) = apply(a, setLookup(key) andThen saved)
    }

    case class OneToManyLens[KB, B, TB <: simple.EntityTable[KB, B]](get: A => OneToManyEnt[KB, B, TB])(val set: OneToManyEnt[KB, B, TB] => A => A) extends LookupLens[OneToManyEnt[KB, B, TB]] {
      def apply(a: A, f: OneToManyEnt[KB, B, TB] => OneToManyEnt[KB, B, TB]) = {
        set(f(get(a)))(a)
      }
      val setLookup = { key: K => o2m: OneToManyEnt[KB, B, TB] => o2m withLookup Lookup(key) }
      def saved(implicit session: simple.Session) = { otm: OneToManyEnt[KB, B, TB] => otm.saved }
    }

    def lookupLenses: Seq[LookupLens[_]] = Nil

    private def updateAndSaveLookupLenses(key: K, a: A)(implicit session: simple.Session) =
      lookupLenses.foldRight(a){ (clu, v) =>
        clu.setLookupAndSave(key, v)
      }

    def insert(v: A)(implicit session: simple.Session): SavedEntity[K, A] = insert(Ent(v))

    def insert(e: Entity[K, A])(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      // Insert it and get the new or old key
      val k2 = e match {
        case ke: KEnt => * returning key insert ke
        case ke: Ent  => forInsert returning key insert ke.value
      }
      // Apply the key to all child lookups (e.g., OneToMany)
      val v2 = updateAndSaveLookupLenses(k2, e.value)
      SavedEntity(k2, v2)
    }
    def update(ke: KEnt)(implicit session: simple.Session): SavedEntity[K, A] = {
      import simple._
      Query(this).where(_.key is ke.key).map(_.forInsert) update ke.value
      SavedEntity(ke.key, updateAndSaveLookupLenses(ke.key, ke.value))
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
    implicit class EntityMapping6[T1, T2, T3, T4, T5, T6](val p: Projection6[T1, T2, T3, T4, T5, T6]) extends EntityMapping[(T1, T2, T3, T4, T5, T6) => A, A => Option[(T1, T2, T3, T4, T5, T6)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6)) }
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
