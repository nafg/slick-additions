package slick
package additions

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

import slick.additions.entity._
import slick.ast._
import slick.jdbc.JdbcProfile
import slick.lifted.{AbstractTable, ForeignKeyQuery, MappedProjection, RepShape, Shape}

import sourcecode.Name


trait AdditionsProfile { this: JdbcProfile =>
  trait AdditionsApi { this: JdbcAPI =>
    implicit def lookupBaseColumnType[K: BaseColumnType, A]: BaseColumnType[Lookup[K, A]] =
      MappedColumnType.base[Lookup[K, A], K](_.key, EntityKey(_))

    trait KeyedTableBase { keyedTable: Table[_] =>
      type Key
      def keyColumnName    = "id"
      def keyColumnOptions = List(O.PrimaryKey, O.AutoInc)
      def key              = column[Key](keyColumnName, keyColumnOptions: _*)
      implicit def keyMapper: TypedType[Key]
    }

    abstract class KeyedTable[K, A](tag: Tag, tableName: String)(implicit val keyMapper: BaseColumnType[K])
        extends Table[A](tag, tableName) with KeyedTableBase {
      type Key = K
    }

    trait EntityTableBase extends KeyedTableBase { this: Table[_] =>
      type Value
      type Ent  = Entity[Key, Value]
      type KEnt = KeyedEntity[Key, Value]
    }

    abstract class EntityTable[K: BaseColumnType, V](tag: Tag, tableName: String)
        extends KeyedTable[K, KeyedEntity[K, V]](tag, tableName) with EntityTableBase {
      type Value = V
      def Ent(v: Value) = new KeylessEntity[Key, Value](v)

      @deprecated("Using key in an EntityTable is unsupported and may lead to crashes", "0.13.0")
      override def key = lookup.asColumnOf[Key]

      def tableQuery: Query[EntityTable[K, V], this.KEnt, Seq]

      def mapping: MappedProjection[V]

      private def all: MappedProjection[KeyedEntity[K, V]] =
        (lookup, mapping).<>(
          {
            case (l, v) => KeyedEntity(l.key, v)
          },
          ke => Some((ke.toEntityKey.asLookup, ke.value))
        )

      def * = all

      def lookup = column[Lookup[K, V]](keyColumnName, keyColumnOptions: _*)
    }

    class KeyedTableQueryBase[K: BaseColumnType, A, T <: KeyedTable[K, A]](cons: Tag => T with KeyedTable[K, A])
        extends TableQuery[T](cons) {
      type Key = K
      type Lookup
    }

    class KeyedTableQuery[K: BaseColumnType, A, T <: KeyedTable[K, A]](cons: Tag => T with KeyedTable[K, A])
        extends KeyedTableQueryBase[K, A, T](cons) with Lookups[K, A, A, T] {

      override def lookupQuery(lookup: Lookup) = this.filter(_.key === lookup.key)
      override def lookupValue(a: A)           = a
    }

    class EntTableQuery[K: BaseColumnType, V, T <: EntityTable[K, V]](cons: Tag => T with EntityTable[K, V])
        extends KeyedTableQueryBase[K, KeyedEntity[K, V], T](cons) with Lookups[K, V, KeyedEntity[K, V], T] {

      type Value = V
      type Ent   = Entity[Key, Value]
      type KEnt  = KeyedEntity[Key, Value]
      def Ent(v: V): Ent = new KeylessEntity[Key, Value](v)

      def lookupQuery(lookup: Rep[Lookup]) = this.filter(_.lookup === lookup)

      override def lookupQuery(lookup: Lookup)       = this.filter(_.lookup === lookup)
      override def lookupValue(a: KeyedEntity[K, V]) = a.value

      def forInsertQuery[E, C[_]](q: Query[T, E, C]) = q.map(_.mapping)

      def insert(v: V)(implicit ec: ExecutionContext): DBIO[SavedEntity[K, V]] = insert(Ent(v): Ent)

      def insert(e: Ent)(implicit ec: ExecutionContext): DBIO[SavedEntity[K, V]] = {
        // Insert it and get the new or old key
        val action = e match {
          case ke: this.KEnt =>
            this returning this.map(_.lookup) forceInsert ke
          case ent: this.Ent =>
            forInsertQuery(this).returning(this.map(_.lookup)) += ent.value
        }
        action.map(l => SavedEntity(l.key, e.value))
      }

      def update(ke: KEnt)(implicit ec: ExecutionContext): DBIO[SavedEntity[K, V]] =
        forInsertQuery(lookupQuery(ke)).update(ke.value)
          .map(_ => SavedEntity(ke.key, ke.value))

      def save(e: Entity[K, V])(implicit ec: ExecutionContext): DBIO[SavedEntity[K, V]] =
        e match {
          case ke: KEnt => update(ke)
          case ke: Ent  => insert(ke)
        }

      def delete(ke: KEnt)(implicit ec: ExecutionContext) = lookupQuery(ke).delete
    }

    trait AutoName { this: Table[_] =>
      def col[A: TypedType](options: ColumnOption[A]*)(implicit name: Name, dbNameStyle: NameStyle) =
        column[A](dbNameStyle.identToDb(name.value), options: _*)

      def col[A: TypedType](implicit name: Name, dbNameStyle: NameStyle) = column[A](dbNameStyle.columnName(name.value))

      def foreign[P, PU, TT <: AbstractTable[_], U](
        sourceColumns: P,
        targetTableQuery: TableQuery[TT]
      )(targetColumns: TT => P,
        onUpdate: ForeignKeyAction = ForeignKeyAction.NoAction,
        onDelete: ForeignKeyAction = ForeignKeyAction.NoAction
      )(implicit
        unpackT: Shape[_ <: FlatShapeLevel, TT, U, _],
        unpackP: Shape[_ <: FlatShapeLevel, P, PU, _],
        name: Name,
        dbNameStyle: NameStyle
      ): ForeignKeyQuery[TT, U] =
        foreignKey(dbNameStyle.foreignKeyName(tableName, name.value), sourceColumns, targetTableQuery)(
          targetColumns,
          onUpdate,
          onDelete
        )

      def idx[A](
        on: A,
        unique: Boolean = false
      )(implicit
        shape: Shape[_ <: FlatShapeLevel, A, _, _],
        name: Name,
        dbNameStyle: NameStyle
      ) = index(dbNameStyle.indexName(tableName, name.value), on, unique = unique)
    }

    trait AutoNameSnakify extends AutoName { this: Table[_] =>
      protected implicit def nameStyle: NameStyle = NameStyle.Snakify
    }

    trait TableModule {
      type Rec
      type Row <: Table[Rec]
      val Q: TableQuery[Row]
      type Q = Query[Row, Rec, Seq]
    }

    abstract class EntityTableModule[K: BaseColumnType, V](tableName: String) extends TableModule {
      type Rec = KeyedEntity[K, V]

      type Lookup = slick.additions.entity.Lookup[K, V]

      abstract class BaseEntRow(tag: Tag) extends EntityTable[K, V](tag, tableName) with AutoNameSnakify {
        def tableQuery = EntityTableModule.this.Q
      }

      type Row <: BaseEntRow

      protected def rowClass: Class[_] = {
        val className = this.getClass.getName.stripSuffix("$") + "$Row"
        Class.forName(className, true, this.getClass.getClassLoader)
      }

      protected def rowConstructor = rowClass.getDeclaredConstructors.head

      protected def mkRow(tag: Tag): Row = rowConstructor.newInstance(tag).asInstanceOf[Row]

      class TableQuery extends EntTableQuery[K, V, Row](mkRow)

      val Q: TableQuery = new TableQuery
    }
  }
}
