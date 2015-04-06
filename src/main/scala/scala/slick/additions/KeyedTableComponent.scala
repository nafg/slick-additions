package scala.slick
package additions

import scala.slick.lifted._
import scala.slick.ast.TypedType
import scala.slick.ast.{ MappedScalaType, Node, Path, Symbol, TypeMapping }
import scala.slick.driver.JdbcDriver
import scala.reflect.{ classTag, ClassTag }
import scala.language.higherKinds

trait KeyedTableComponent extends JdbcDriver {
  trait Lookups[K, A] {
    import simple.{ BaseColumnType => _, MappedColumnType => _, _ }
    type TableType
    def lookupQuery(lookup: Lookup): Query[TableType, A, Seq]
    sealed abstract class Lookup {
      def key: K
      def value: Option[A]
      def query: Query[TableType, A, Seq] = lookupQuery(this)

      def fetched(implicit session: Session) =
        query.firstOption.fold(this)(a => Lookup.Fetched(key, a))
      def apply()(implicit session: Session) = fetched.value
    }
    object Lookup {
      case object NotSet extends Lookup {
        def key = throw new NoSuchElementException("key of NotSetLookup")
        def value = None
        override def fetched(implicit session: Session) = this
      }
      final case class Unfetched(key: K) extends Lookup {
        def value = None
      }
      final case class Fetched(key: K, ent: A) extends Lookup {
        def value = Some(ent)
        override def apply()(implicit session: Session) = value
      }
      def apply(key: K): Lookup = Unfetched(key)
      def apply(key: K, precache: A): Lookup = Fetched(key, precache)
      implicit def lookupMapper(implicit bctk: BaseColumnType[K]): BaseColumnType[Lookup] = MappedColumnType.base[Lookup, K](_.key, Lookup(_))
    }
  }

  trait KeyedTableBase  { keyedTable: Table[_] =>
    type Key
    def keyColumnName = "id"
    def keyColumnOptions = List(O.PrimaryKey, O.NotNull, O.AutoInc)
    def key = column[Key](keyColumnName, keyColumnOptions: _*)
    implicit def keyMapper: TypedType[Key]
  }

  abstract class KeyedTable[K, A](tag: Tag, tableName: String)(implicit val keyMapper: BaseColumnType[K]) extends Table[A](tag, tableName) with KeyedTableBase {
    type Key = K
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

    def tableQuery: Query[EntityTable[K, V], KEnt, Seq]

    implicit class MapProj[Value](value: Value) {
      def <->[R : ClassTag, Unpacked](construct: Option[K] => Unpacked => R, extract: R => Option[Unpacked])(implicit shape: Shape[_ <: FlatShapeLevel, Value, Unpacked, _]): MappedProj[Value, Unpacked, R] =
        new MappedProj[Value, Unpacked, R](value, construct, extract(_).get)(shape, classTag[R])
    }
    implicit class MapProjShapedValue[T, U](v: ShapedValue[T, U]) {
      def <->[R : ClassTag](construct: Option[K] => U => R, extract: R => Option[U]): MappedProj[T, U, R] =
        new MappedProj[T, U, R](v.value, construct, extract(_).get)(v.shape, classTag[R])
    }

    class MappedProj[Src, Unpacked, MappedAs](val source: Src, val construct: (Option[K] => Unpacked => MappedAs), val extract: (MappedAs => Unpacked))(implicit val shape: Shape[_ <: FlatShapeLevel, Src, Unpacked, _], tag: ClassTag[MappedAs]) extends Rep[MappedAs] {
      override def toNode: Node = TypeMapping(
        shape.toNode(source),
        MappedScalaType.Mapper(
          v => extract(v.asInstanceOf[MappedAs]),
          v => construct(None)(v.asInstanceOf[Unpacked]),
          None
        ),
        tag
      )

      def encodeRef(path: List[Symbol]): MappedProj[Src, Unpacked, MappedAs] = new MappedProj[Src, Unpacked, MappedAs](source, construct, extract)(shape, tag) {
        override def toNode = Path(path)
      }

      def ~:(kc: Column[K])(implicit kShape: Shape[_ <: FlatShapeLevel, Column[K], K, _]): MappedProjection[KeyedEntity[K, MappedAs], (K, Unpacked)] = {
        val ksv = new ToShapedValue(kc).shaped
        val ssv: ShapedValue[Src, Unpacked] = new ToShapedValue(source).shaped
        (ksv zip ssv).<>[KeyedEntity[K, MappedAs]](
          { case (k, v) => SavedEntity(k, construct(Some(k))(v)): KeyedEntity[K, MappedAs] },
          ke => Some((ke.key, extract(ke.value))))
      }
    }

    object MappedProj {
      implicit class IdentityProj[V, P : ClassTag](value: V)(implicit shape: Shape[_ <: FlatShapeLevel, V, P, _])
        extends MappedProj[V, P, P](value, _ => identity[P], identity[P])(shape, classTag[P])
    }

    def mapping: MappedProj[_, _, V]

    private def all: MappedProjection[KeyedEntity[K, V], _ <: (K, _)] =
      key ~: mapping

    def * = all
  }

  class KeyedTableQuery[K : BaseColumnType, A, T <: KeyedTable[K, A]](cons: Tag => (T with KeyedTable[K, A])) extends TableQuery[T](cons) with Lookups[K, A] {
    import simple.{ BaseColumnType => _, MappedColumnType => _, _ }
    type Key = K
    type TableType = T

    override def lookupQuery(lookup: Lookup) = lookup match {
      case Lookup.NotSet => this.filter(_ => LiteralColumn(false))
      case _             => this.filter(_.key === lookup.key)
    }

    val lookup: T => Column[Lookup] = t => t.key.asColumnOf[Lookup]

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
        copy(xs, isFetched = true)
      }

      def query: Query[T2, KeyedEntity[K2, V2], Seq] = {
        def ot = otherTable
        thisLookup match {
          case None =>
            ot filter (_ => LiteralColumn(false))
          case Some(lu) =>
            ot filter (column(_) === lu)
        }
      }

      def deleteQuery(keep: Seq[K2]) =
        query.filter {
          case t: simple.EntityTable[K2, V2] =>
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

    implicit val mappingRepShape = RepShape[FlatShapeLevel, T#MappedProj[_, _, V], V]

    def forInsertQuery[E, C[_]](q: Query[T, E, C]) = q.map(_.mapping)

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
      forInsertQuery(this.filter(_.key === ke.key)) update ke.value
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
      this.filter(_.key === ke.key).delete
    }
  }

  trait SimpleQL extends super.SimpleQL {
    type KeyedTable[K, A] = KeyedTableComponent.this.KeyedTable[K, A]
    type EntityTable[K, A] = KeyedTableComponent.this.EntityTable[K, A]
    type Ent[T <: EntityTableBase] = Entity[T#Key, T#Value]
    type KEnt[T <: EntityTableBase] = KeyedEntity[T#Key, T#Value]
    def Ent[T <: EntityTableBase](value: T#Value) = new KeylessEntity[T#Key, T#Value](value)
    type Lookups[K, A] = KeyedTableComponent.this.Lookups[K, A]
    type KeyedTableQuery[K, A, T <: KeyedTable[K, A]] = KeyedTableComponent.this.KeyedTableQuery[K, A, T]
    type EntTableQuery[K, V, T <: EntityTable[K, V]] = KeyedTableComponent.this.EntTableQuery[K, V, T]
  }
  override val simple: SimpleQL with Implicits = new SimpleQL with Implicits {}
}