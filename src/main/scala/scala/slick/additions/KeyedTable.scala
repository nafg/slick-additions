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
  trait KeyedTableBase  { keyedTable: Table[_] =>
    type Key
    def keyColumnName = "id"
    def keyColumnOptions = List(O.PrimaryKey, O.NotNull, O.AutoInc)
    def key = column[Key](keyColumnName, keyColumnOptions: _*)
    implicit def keyMapper: BaseTypeMapper[Key]
  }

  abstract class KeyedTableLookups[K, A](tableName: String)(implicit val keyMapper: BaseTypeMapper[K]) extends Table[A](tableName) with KeyedTableBase {
    import simple._

    type Key = K

    sealed trait Lookup {
      def key: Key
      def value: Option[A]
      def query: Query[KeyedTableLookups[K, A], A] = {
        Query(KeyedTableLookups.this).filter(_.key is key)
      }
      def fetched(implicit session: Session) =
        query.firstOption map { a => Fetched(key, a) } getOrElse this
      def apply()(implicit session: Session) = fetched.value
    }
    final case class Unfetched(key: Key) extends Lookup {
      def value = None
    }
    final case class Fetched(key: Key, ent: A) extends Lookup {
      def value = Some(ent)
      override def apply()(implicit session: Session) = value
    }

    def Lookup(key: K): Lookup = Unfetched(key)
    def Lookup(key: K, precache: A): Lookup = Fetched(key, precache)

    def lookup: Column[Lookup] = column[Lookup](keyColumnName, keyColumnOptions: _*)

    implicit def lookupMapper: BaseTypeMapper[Lookup] =
      MappedTypeMapper.base[Lookup, K](_.key, Lookup(_))
  }

  abstract class KeyedTable[K: BaseTypeMapper, A](tableName: String) extends KeyedTableLookups[K, A](tableName) {
    class OneToMany[TB <: EntityTableBase](
      private[KeyedTable] val otherTable: TB with EntityTable[TB#Key, TB#Value],
      private[KeyedTable] val thisLookup: Option[Lookup]
    )(
      private[KeyedTable] val column: TB => Column[Lookup],
      private[KeyedTable] val setLookup: Lookup => TB#Value => TB#Value
    )(
      val items: Seq[Entity[TB#Key, TB#Value]] = Nil,
      val isFetched: Boolean = false
    ) {
      type BEnt = Entity[TB#Key, TB#Value]

      def values = items map (_.value)

      private def thisTable = KeyedTable.this

      override def equals(o: Any) = o match {
        case that: OneToMany[TB] =>
          this.thisTable == that.thisTable &&
          this.otherTable == that.otherTable &&
          scala.slick.ast.Node(this.column(this.otherTable)) == scala.slick.ast.Node(that.column(that.otherTable)) &&
          this.items.toSet == that.items.toSet
        case _ => false
      }

      def copy(items: Seq[BEnt], isFetched: Boolean = isFetched) =
        new OneToMany[TB](otherTable, thisLookup)(column, setLookup)(items, isFetched)

      def map(f: Seq[BEnt] => Seq[BEnt]) = copy(f(items))

      def withLookup(lookup: Lookup): OneToMany[TB] =
        new OneToMany[TB](otherTable, Some(lookup))(column, setLookup)(items map { e => e.map(setLookup(lookup)) }, isFetched)

      import simple._

      def saved(implicit session: Session): OneToMany[TB] = {
        if(isFetched) {
          val dq = deleteQuery(items.collect { case ke: KeyedEntity[TB#Key, TB#Value] => ke.key })
          dq.delete
        }
        val xs = items map {
          case e: Entity[TB#Key, TB#Value] =>
            if(e.isSaved) e
            else otherTable save e
        }
        copy(xs, true)
      }

      def query: Query[TB with simple.EntityTable[TB#Key, TB#Value], KeyedEntity[TB#Key, TB#Value]] = {
        def ot = otherTable
        thisLookup match {
          case None =>
            Query(ot) where (_ => ConstColumn(false))
          case Some(lu) =>
            Query(ot) where (column(_) is lu)
        }
      }

      def deleteQuery(keep: Seq[TB#Key]) =
        query.filter {
          case t: TB with simple.EntityTable[TB#Key, TB#Value] =>
            implicit def tm: BaseTypeMapper[TB#Key] = t.keyMapper
            !(t.key inSet keep)
        }

      def fetched(implicit session: Session) = copy(query.list, true)

      def apply()(implicit session: simple.Session) = if(isFetched) items else fetched.items

      override def toString = s"${KeyedTable.this.getClass.getSimpleName}.OneToMany($items)"
    }

    def OneToMany[TB <: EntityTableBase](
      otherTable: TB with EntityTable[TB#Key, TB#Value], lookup: Option[Lookup]
    )(
      column: TB => Column[Lookup], setLookup: Lookup => TB#Value => TB#Value, initial: Seq[Entity[TB#Key, TB#Value]] = null
    ): OneToMany[TB] = {
      val init = Option(initial)
      new OneToMany[TB](otherTable, lookup)(column, setLookup)(init getOrElse Nil, init.isDefined)
    }
  }

  trait EntityTableBase extends KeyedTableBase { this: Table[_] =>
    type Key
    type Value
    type Ent = Entity[Key, Value]
    type KEnt = KeyedEntity[Key, Value]

    def Ent(v: Value): KeylessEntity[Key, Value]

    def save(e: Ent)(implicit session: simple.Session): SavedEntity[Key, Value]
    def delete(ke: KEnt)(implicit session: simple.Session): Int
  }

  abstract class EntityTable[K: BaseTypeMapper, A](tableName: String) extends KeyedTable[K, KeyedEntity[K, A]](tableName) with EntityTableBase {
    type Value = A
    def Ent(v: Value) = new KeylessEntity[Key, Value](v)

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

    case class OneToManyLens[TB <: EntityTableBase](get: A => OneToMany[TB])(val set: OneToMany[TB] => A => A) extends LookupLens[OneToMany[TB]] {
      def apply(a: A, f: OneToMany[TB] => OneToMany[TB]) = {
        set(f(get(a)))(a)
      }
      val setLookup = { key: K => o2m: OneToMany[TB] => o2m withLookup Lookup(key) }
      def saved(implicit session: simple.Session) = { otm: OneToMany[TB] => otm.saved }
    }


    def lookupLenses: Seq[LookupLens[_]] = Nil

    private def updateAndSaveLookupLenses(key: K, a: A)(implicit session: simple.Session) =
      lookupLenses.foldRight(a){ (clu, v) =>
        clu.setLookupAndSave(key, v)
      }

    def insert(v: A)(implicit session: simple.Session): SavedEntity[K, A] = insert(Ent(v): Ent)

    def insert(e: Ent)(implicit session: simple.Session): SavedEntity[K, A] = {
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

    implicit class EntityMapping7[T1, T2, T3, T4, T5, T6, T7](val p: Projection7[T1, T2, T3, T4, T5, T6, T7]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7)) }
        )
      )
    }

    implicit class EntityMapping8[T1, T2, T3, T4, T5, T6, T7, T8](val p: Projection8[T1, T2, T3, T4, T5, T6, T7, T8]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)) }
        )
      )
    }

    implicit class EntityMapping9[T1, T2, T3, T4, T5, T6, T7, T8, T9](val p: Projection9[T1, T2, T3, T4, T5, T6, T7, T8, T9]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)) }
        )
      )
    }

    implicit class EntityMapping10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](val p: Projection10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)) }
        )
      )
    }

    implicit class EntityMapping11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](val p: Projection11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)) }
        )
      )
    }

    implicit class EntityMapping12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](val p: Projection12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)) }
        )
      )
    }

    implicit class EntityMapping13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](val p: Projection13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)) }
        )
      )
    }

    implicit class EntityMapping14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](val p: Projection14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)) }
        )
      )
    }

    implicit class EntityMapping15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](val p: Projection15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)) }
        )
      )
    }

    implicit class EntityMapping16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](val p: Projection16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16)) }
        )
      )
    }

    implicit class EntityMapping17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](val p: Projection17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17)) }
        )
      )
    }

    implicit class EntityMapping18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](val p: Projection18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18)) }
        )
      )
    }

    implicit class EntityMapping19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](val p: Projection19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19)) }
        )
      )
    }

    implicit class EntityMapping20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](val p: Projection20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20)) }
        )
      )
    }

    implicit class EntityMapping21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](val p: Projection21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]) extends EntityMapping[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => A, A => Option[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)) => SavedEntity(t._1, ap(Some(t._1))(t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21, t._22)),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21)) }
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
  trait SimpleQL extends super.SimpleQL
  override val simple: SimpleQL = new SimpleQL {}
}
