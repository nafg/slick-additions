package scala.slick
package additions

import scala.slick.lifted._
import scala.slick.direct.AnnotationMapper.column
import scala.slick.profile.RelationalProfile
import scala.slick.profile.RelationalDriver
import scala.slick.ast.ColumnOption
import scala.slick.ast.TypedType
import scala.slick.ast.BaseTypedType
import scala.slick.ast.{ Node, Path, Symbol, TypeMapping }
import scala.slick.driver.JdbcDriver

trait KeyedTableComponent extends JdbcDriver {
  trait KeyedTableBase  { keyedTable: Table[_] =>
    type Key
    def keyColumnName = "id"
    def keyColumnOptions = List(O.PrimaryKey, O.NotNull, O.AutoInc)
    def key = column[Key](keyColumnName, keyColumnOptions: _*)
    implicit def keyMapper: TypedType[Key]
    def tableQuery: Query[KeyedTableBase, _]
  }

  abstract class KeyedTable[K, A](tag: Tag, tableName: String)(implicit val keyMapper: BaseColumnType[K]) extends Table[A](tag, tableName) with KeyedTableBase {
    type Key = K

    def tableQuery: Query[KeyedTable[K, A], A]
  }

  trait EntityTableBase extends KeyedTableBase { this: Table[_] =>
    type Value
    type Ent = Entity[Key, Value]
    type KEnt = KeyedEntity[Key, Value]
  }

  abstract class EntityTable[K : BaseColumnType, V](tag: Tag, tableName: String) extends KeyedTable[K, KeyedEntity[K, V]](tag, tableName) with EntityTableBase {
    type Value = V
    def Ent(v: Value) = new KeylessEntity[Key, Value](v)

    import simple.{ EntityTable => _, _ }

    def tableQuery: Query[EntityTable[K, V], KEnt]

    implicit class MapProj[Value](value: Value) {
      def <->[R, Unpacked](construct: Option[K] => Unpacked => R, extract: R => Option[Unpacked])(implicit shape: Shape[_ <: ShapeLevel.Flat, Value, Unpacked, _]): MappedProj[Value, Unpacked, R] =
        new MappedProj[Value, Unpacked, R](value, construct, extract(_).get)(shape)
    }
    implicit class MapProjShapedValue[T, U](v: ShapedValue[T, U]) {
      def <->[R](construct: Option[K] => U => R, extract: R => Option[U]): MappedProj[T, U, R] =
        new MappedProj[T, U, R](v.value, construct, extract(_).get)(v.shape)
    }

    class MappedProj[Src, Unpacked, MappedAs](val source: Src, val construct: (Option[K] => Unpacked => MappedAs), val extract: (MappedAs => Unpacked))(implicit val shape: Shape[_ <: ShapeLevel.Flat, Src, Unpacked, _]) extends ColumnBase[MappedAs] {

      override def toNode: Node = TypeMapping(shape.toNode(source), (v => extract(v.asInstanceOf[MappedAs])), (v => construct(None)(v.asInstanceOf[Unpacked])))

      def encodeRef(path: List[Symbol]): MappedProj[Src, Unpacked, MappedAs] = new MappedProj[Src, Unpacked, MappedAs](source, construct, extract)(shape) {
        override def toNode = Path(path)
      }

      def ~:(kc: Column[K])(implicit kShape: Shape[_ <: ShapeLevel.Flat, Column[K], K, _]): MappedProjection[KeyedEntity[K, MappedAs], (K, Unpacked)] = {
        val ksv = new ToShapedValue(kc).shaped
        val ssv: ShapedValue[Src, Unpacked] = new ToShapedValue(source).shaped
        (ksv zip ssv).<>[KeyedEntity[K, MappedAs]](
          { case (k, v) => SavedEntity(k, construct(Some(k))(v)): KeyedEntity[K, MappedAs] },
          ke => Some((ke.key, extract(ke.value))))
      }
    }

    object MappedProj {
      implicit def identityProj[V, P](value: V)(implicit shape: Shape[_ <: ShapeLevel.Flat, V, P, _]): MappedProj[V, P, P] =
        new MappedProj[V, P, P](value, _ => identity[P], identity[P])(shape)
    }

    def mapping: MappedProj[_, _, V]

    private def all: MappedProjection[KeyedEntity[K, V], _ <: (K, _)] =
      key ~: mapping

    def * = all
  }

  class KeyedTableQuery[K : BaseColumnType, A, T <: KeyedTable[K, A]](cons: Tag => T) extends TableQuery[T](cons) {
    import simple.{ BaseColumnType => _, MappedColumnType => _, _ }
    type Key = K
    sealed trait Lookup {
      def key: Key
      def value: Option[A]
      def query: Query[T, A] = KeyedTableQuery.this.filter(_.key is key)

      def fetched(implicit session: Session) =
        query.firstOption map { a => Lookup.Fetched(key, a) } getOrElse this
      def apply()(implicit session: Session) = fetched.value
    }
    object Lookup {
      case object NotSet extends Lookup {
        def key = throw new NoSuchElementException("key of NotSetLookup")
        def value = None
      }
      final case class Unfetched(key: Key) extends Lookup {
        def value = None
      }
      final case class Fetched(key: Key, ent: A) extends Lookup {
        def value = Some(ent)
        override def apply()(implicit session: Session) = value
      }
      def apply(key: K): Lookup = Unfetched(key)
      def apply(key: K, precache: A): Lookup = Fetched(key, precache)
      implicit def lookupMapper: BaseColumnType[Lookup] = MappedColumnType.base[Lookup, K](_.key, Lookup(_))
    }

    val lookup: T => Column[Lookup] = t => t.column[Lookup](t.keyColumnName, t.keyColumnOptions: _*)


    class OneToMany[K2, V2, T2 <: simple.EntityTable[K2, V2]](
      private[KeyedTableQuery] val otherTable: simple.EntTableQuery[K2, V2, T2],
      private[KeyedTableQuery] val thisLookup: Option[Lookup]
    )(
      private[KeyedTableQuery] val column: T2 => Column[Lookup],
      private[KeyedTableQuery] val setLookup: Lookup => V2 => V2
    )(
      val items: Seq[Entity[K2, V2]] = Nil,
      val isFetched: Boolean = false
    ) {
      type BEnt = Entity[K2, V2]

      def values = items map (_.value)

      private def thisTable = KeyedTableQuery.this

      override def equals(o: Any) = o match {
        case that: OneToMany[_, _, _] =>
          this.thisTable == that.thisTable &&
          this.otherTable == that.otherTable &&
          this.column(this.otherTable.baseTableRow).toNode == that.column(that.otherTable.baseTableRow).toNode &&
          this.items.toSet == that.items.toSet
        case _ => false
      }

      def copy(items: Seq[BEnt], isFetched: Boolean = isFetched) =
        new OneToMany[K2, V2, T2](otherTable, thisLookup)(column, setLookup)(items, isFetched)

      def map(f: Seq[BEnt] => Seq[BEnt]) = copy(f(items))

      def withLookup(lookup: Lookup): OneToMany[K2, V2, T2] =
        new OneToMany[K2, V2, T2](otherTable, Some(lookup))(column, setLookup)(items map { e => e.map(setLookup(lookup)) }, isFetched)

      import simple.{ BaseColumnType => _, _ }

      def saved(implicit session: Session): OneToMany[K2, V2, T2] = {
        if(isFetched) {
          val dq = deleteQuery(items.collect { case ke: KeyedEntity[K2, V2] => ke.key })
          dq.delete
        }
        val xs = items map {
          case e: Entity[K2, V2] =>
            if(e.isSaved) e
            else otherTable save e
        }
        copy(xs, true)
      }

      def query: Query[T2, KeyedEntity[K2, V2]] = {
        def ot = otherTable.asInstanceOf[Query[T2, KeyedEntity[K2, V2]]]
        thisLookup match {
          case None =>
            ot where (_ => LiteralColumn(false))
          case Some(lu) =>
            ot where (column(_) is lu)
        }
      }

      def deleteQuery(keep: Seq[K2]) =
        query.filter {
          case t: T2 with simple.EntityTable[K2, V2] =>
            implicit def tm: BaseColumnType[K2] = t.keyMapper
            !(t.key inSet keep)
        }

      def fetched(implicit session: Session) = copy(query.list, true)

      override def toString = s"${KeyedTableQuery.this.getClass.getSimpleName}.OneToMany($items)"
    }

    def OneToMany[K2, V2, T2 <: simple.EntityTable[K2, V2]](
      otherTable: simple.EntTableQuery[K2, V2, T2], lookup: Option[Lookup]
    )(
      column: T2 => Column[Lookup], setLookup: Lookup => V2 => V2, initial: Seq[Entity[K2, V2]] = null
    ): OneToMany[K2, V2, T2] = {
      val init = Option(initial)
      new OneToMany[K2, V2, T2](otherTable, lookup)(column, setLookup)(init getOrElse Nil, init.isDefined)
    }

  }

  class EntTableQuery[K : BaseColumnType, V, T <: EntityTable[K, V]](cons: Tag => T with EntityTable[K, V]) extends KeyedTableQuery[K, KeyedEntity[K, V], T](cons) {
    import simple.{ BaseColumnType => _, MappedColumnType => _, _ }
    type Value = V
    type Ent = Entity[Key, Value]
    type KEnt = KeyedEntity[Key, Value]
    def Ent(v: V): Ent = new KeylessEntity[Key, Value](v)

    trait LookupLens[L] {
      /**
       * Get the field on an entity value that
       * serves as the foreign key lookup
       * for the other table
       */
      def get: V => L
      /**
       * Set an entity value's lookup
       * object, returning a new, modified
       * entity value
       */
      def set: L => V => V
      /**
       * Modify an entity value's lookup
       * relative to itself
       * @param a the entity value
       * @param f a function that transforms a lookup object
       * @return an entity value with the new lookup
       */
      def apply(v: V, f: L => L): V
      /**
       * Create a lookup object transformer that
       * synchronizes the lookup object to the database
       * (which may generate new keys)
       */
      def saved(implicit session: simple.Session): L => L
      /**
       * Set the lookup object's key
       */
      def setLookup: K => L => L
      def setLookupAndSave(key: K, v: V)(implicit session: simple.Session) = apply(v, setLookup(key) andThen saved)
    }

    case class OneToManyLens[K2, V2, T2 <: simple.EntityTable[K2, V2]](get: V => OneToMany[K2, V2, T2])(val set: OneToMany[K2, V2, T2] => V => V) extends LookupLens[OneToMany[K2, V2, T2]] {
      def apply(v: V, f: OneToMany[K2, V2, T2] => OneToMany[K2, V2, T2]) = {
        set(f(get(v)))(v)
      }
      val setLookup = { key: K => o2m: OneToMany[K2, V2, T2] => o2m withLookup Lookup(key) }
      def saved(implicit session: simple.Session) = { otm: OneToMany[K2, V2, T2] => otm.saved }
    }


    def lookupLenses: Seq[LookupLens[_]] = Nil

    private def updateAndSaveLookupLenses(key: K, v: V)(implicit session: simple.Session) =
      lookupLenses.foldRight(v){ (clu, v) =>
        clu.setLookupAndSave(key, v)
      }

    def forInsertQuery[E](q: Query[T, E]) = q.map(_.mapping)

    def insert(v: V)(implicit session: simple.Session): SavedEntity[K, V] = insert(Ent(v): Ent)

    def insert(e: Ent)(implicit session: simple.Session): SavedEntity[K, V] = {
      // Insert it and get the new or old key
      val k2 = e match {
        case ke: this.KEnt =>
          this returning this.map(_.key: Column[Key]) forceInsert ke
        case ke: this.Ent  =>
          forInsertQuery(this) returning this.map(_.key) insert ke.value
      }
      // Apply the key to all child lookups (e.g., OneToMany)
      val v2 = updateAndSaveLookupLenses(k2, e.value)
      SavedEntity(k2, v2)
    }
    def update(ke: KEnt)(implicit session: simple.Session): SavedEntity[K, V] = {
      forInsertQuery(this.where(_.key is ke.key)) update ke.value
      SavedEntity(ke.key, updateAndSaveLookupLenses(ke.key, ke.value))
    }
    def save(e: Entity[K, V])(implicit session: simple.Session): SavedEntity[K, V] = {
      e match {
        case ke: KEnt => update(ke)
        case ke: Ent  => insert(ke)
      }
    }
    def delete(ke: KEnt)(implicit session: simple.Session) = {
      import simple._
      this.filter(_.key is ke.key).delete
    }
  }

  trait SimpleQL extends super.SimpleQL {
    type KeyedTable[K, A] = KeyedTableComponent.this.KeyedTable[K, A]
    type EntityTable[K, A] = KeyedTableComponent.this.EntityTable[K, A]
    type Ent[T <: EntityTableBase] = Entity[T#Key, T#Value]
    type KEnt[T <: EntityTableBase] = KeyedEntity[T#Key, T#Value]
    def Ent[T <: EntityTableBase](value: T#Value) = new KeylessEntity[T#Key, T#Value](value)
    type KeyedTableQuery[K, A, T <: KeyedTable[K, A]] = KeyedTableComponent.this.KeyedTableQuery[K, A, T]
    type EntTableQuery[K, V, T <: EntityTable[K, V]] = KeyedTableComponent.this.EntTableQuery[K, V, T]
  }
  override val simple: SimpleQL with Implicits = new SimpleQL with Implicits {}
}
