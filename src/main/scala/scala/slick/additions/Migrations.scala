package scala.slick
package additions

import lifted._
import driver._
import ast._
import session.Session

trait Migrations extends BasicSQLUtilsComponent with BasicStatementBuilderComponent { driver: BasicDriver =>
  protected def fieldSym(node: Node): Option[FieldSymbol] = node match {
    case Select(_, f: FieldSymbol) => Some(f)
    case _                         => None
  }
  protected def quotedColumnNames(ns: Seq[Node]) = ns.flatMap(fieldSym).map(fs => quoteIdentifier(fs.name))
  protected def tableName(node: Node) = node match {
    case TableNode(n) => quoteIdentifier(n)
    case _            => sys.error("Not a TableNode")
  }

  trait Migration {
    def apply(implicit session: Session): Unit
    def &(next: Migration) = MigrationSeq(this, next)
  }
  trait ReversibleMigration extends Migration {
    def down: Migration
    def &(next: ReversibleMigration) = new ReversibleMigrationSeq(this, next)
  }
  trait SqlMigration extends Migration {
    def sql: String
    def apply(implicit session: Session) = session.withStatement()(_ execute sql)
  }
  object SqlMigration {
    def apply(sql0: String) = new SqlMigration { def sql = sql0 }
  }
  case class MigrationSeq(migrations: Migration*) extends Migration {
    override final def &(next: Migration) = MigrationSeq(migrations :+ next: _*)
    final def apply(implicit session: Session) = migrations foreach (_(session))
  }
  class ReversibleMigrationSeq(migrations: ReversibleMigration*) extends MigrationSeq(migrations: _*) with ReversibleMigration {
    override final def &(next: ReversibleMigration) = new ReversibleMigrationSeq(migrations :+ next: _*)
    final def down = MigrationSeq(migrations.reverse.map(_.down): _*)
  }
  class ColumnOptions(column: FieldSymbol) {
    import ColumnOption._
    val tmDelegate = column.typeMapper(driver)
    val (sqlType, notNull, autoInc, pk, dflt) =
      column.options.foldLeft((tmDelegate.sqlTypeName, !tmDelegate.nullable, false, false, Option.empty[String])){
        case (t, DBType(s))  => t.copy(_1 = s)
        case (t, NotNull)    => t.copy(_2 = true)
        case (t, Nullable)   => t.copy(_2 = false)
        case (t, AutoInc)    => t.copy(_3 = true)
        case (t, PrimaryKey) => t.copy(_4 = true)
        case (t, Default(v)) =>
          val lit = column.typeMapper(driver).asInstanceOf[TypeMapperDelegate[Any]].valueToSQLLiteral(v)
          t.copy(_5 = Some(lit))
        case (t, _)          => t
      }
  }
  def columnSql(column: FieldSymbol) = {
    val opts = new ColumnOptions(column)
    import opts._
    def name = quoteIdentifier(column.name)
    def typ = (if(autoInc) "SERIAL" else sqlType)
    def options =
      dflt.map(" DEFAULT " + _).getOrElse("") +
      (if(notNull) " NOT NULL") +
      (if(pk) " PRIMARY KEY")
    name + " " + typ + options
  }
  trait CreateTableBase[T <: Table[_]] extends ReversibleMigration { outer =>
    def table: T
    class Proxy(extra: ReversibleMigrationSeq) extends CreateTableBase[T] {
      def table = outer.table
      def self = outer & extra
      def apply(implicit session: Session) = self.apply(session)
      def down = self.down
    }
    def withForeignKeys(fks: (T => ForeignKeyQuery[_ <: TableNode, _])*) = new Proxy(
      new ReversibleMigrationSeq(fks.map(f => CreateForeignKey(f(table))): _*)
    )
    def withPrimaryKeys(pks: (T => PrimaryKey)*) = new Proxy(
      new ReversibleMigrationSeq(pks.map(f => CreatePrimaryKey(table, f(table))): _*)
    )
    def withIndexes(is: (T => Index)*) = new Proxy(
      new ReversibleMigrationSeq(is.map(f => CreateIndex(f(table))): _*)
    )
  }
  case class CreateTable[T <: Table[_]](table: T)(columns: (T => Column[_])*) extends SqlMigration with CreateTableBase[T] {
    protected val fss = columns flatMap (f => fieldSym(Node(f(table))))
    def sql = s"""create table
      ${ tableName(table) } (
      ${ fss map columnSql mkString ", " }
    )"""
    def down = DropTable(table)
  }
  case class DropTable(table: Table[_]) extends SqlMigration {
    def sql = s"drop table ${ tableName(table) }"
  }
  object CreateForeignKey {
    def apply(fkq: ForeignKeyQuery[_ <: TableNode, _]): ReversibleMigrationSeq =
     new ReversibleMigrationSeq(fkq.fks.map(new CreateForeignKey(_)): _*)
  }
  case class CreateForeignKey(fk: ForeignKey[_ <: TableNode, _]) extends SqlMigration with ReversibleMigration {
    def sql = s"""alter table ${ tableName(fk.sourceTable) }
      add constraint ${ quoteIdentifier(fk.name)}
      foreign key ( ${ quotedColumnNames(fk.linearizedSourceColumns) mkString ", " } )
      references ${ tableName(fk.targetTable) }
      ( ${ quotedColumnNames(fk.linearizedTargetColumnsForOriginalTargetTable) mkString ", " } )
      on update ${ fk.onUpdate.action }
      on delete ${ fk.onDelete.action }
    """
    def down = new DropForeignKey(fk)
  }
  object DropForeignKey {
    def apply(fkq: ForeignKeyQuery[_ <: TableNode, _]): ReversibleMigrationSeq =
     new ReversibleMigrationSeq(fkq.fks.map(new DropForeignKey(_)): _*)
  }
  case class DropForeignKey(fk: ForeignKey[_ <: TableNode, _]) extends SqlMigration with ReversibleMigration {
    def sql = s"""alter table ${ tableName(fk.sourceTable) }
      drop constraint ${ quoteIdentifier(fk.name)}
    """
    def down = new CreateForeignKey(fk)
  }
  case class CreatePrimaryKey(table: Table[_], key: PrimaryKey) extends SqlMigration with ReversibleMigration {
    def sql = s"""alter table ${ tableName(table) }
      add constraint ${ quoteIdentifier(key.name) }
      primary key
      ( ${ quotedColumnNames(key.columns) mkString ", " } )
    """
    def down = DropPrimaryKey(table, key)
  }
  case class DropPrimaryKey(table: Table[_], key: PrimaryKey) extends SqlMigration with ReversibleMigration {
    def sql = s"""alter table ${ tableName(table) }
      drop constraint ${ quoteIdentifier(key.name) }
    """
    def down = CreatePrimaryKey(table, key)
  }
  case class CreateIndex(index: Index) extends SqlMigration with ReversibleMigration {
    def sql = s"""create ${ if(index.unique) "unique" else "" }
      index ${ quoteIdentifier(index.name) }
      on  ${ tableName(index.table) }
      ( ${ quotedColumnNames(index.on) mkString ", " } )
    """
    def down = DropIndex(index)
  }
  case class DropIndex(index: Index) extends SqlMigration with ReversibleMigration {
    def sql = s"""drop index ${ quoteIdentifier(index.name) }"""
    def down = CreateIndex(index)
  }
  protected def tableAndFS(column: Column[_]) = Node(column) match {
    case Select(TableNode(n), f: FieldSymbol) => (quoteIdentifier(n), f)
  }
  case class AddColumn(column: Column[_]) extends SqlMigration with ReversibleMigration {
    private[this] val (table, fs) = tableAndFS(column)
    def sql = s"""alter table ${ table }
      add column ${ columnSql(fs) }
    """
    def down = DropColumn(column)
  }
  case class DropColumn(column: Column[_]) extends SqlMigration with ReversibleMigration {
    private[this] val (table, fs) = tableAndFS(column)
    def sql = s"""alter table ${ table }
      drop column ${ quoteIdentifier(fs.name) }
    """
    def down = AddColumn(column)
  }
  /**
   * Can rename too
   */
  case class AlterColumnType(column: Column[_]) extends SqlMigration {
    private[this] val (table, fs) = tableAndFS(column)
    private[this] val options = new ColumnOptions(fs)
    def sql = s"""
      alter table ${ table }
      alter column ${ fs.name }
      type ${ options.sqlType }
    """
  }
  case class AlterColumnDefault(column: Column[_]) extends SqlMigration {
    private[this] val (table, fs) = tableAndFS(column)
    private[this] val options = new ColumnOptions(fs)
    def sql = s"""
      alter table ${ table}
      alter column ${ fs.name }
      set default ${ options.dflt getOrElse "null" }
    """
  }
  case class AlterColumnNullability(column: Column[_]) extends SqlMigration {
    private[this] val (table, fs) = tableAndFS(column)
    private[this] val options = new ColumnOptions(fs)
    def sql = s"""
      alter table ${ table}
      alter column ${ fs.name }
      ${ if(options.notNull) "set" else "drop" } not null
    """
  }
}

/*def schemification(implicit session: Session) = new TableDDLBuilder(this) {
      override def createTable: String = {
        val tables = MTable.getTables(None, None, None, Some(List("TABLE"))).list
        val b = new StringBuilder
        tables.find(_.name.name == tableName) match { // TODO schema (we have to know the default, it == None)
          case Some(mqt) =>
            var first = true
            def checkFirst = if(first) {
              b append "alter table "
              b append quoteIdentifier(table.tableName) append " "
              first = false
            } else b append ","
            val columns = create_*
            val existingCols = mqt.getColumns.list
            for(c <- columns) {
              existingCols.find(_.column == c.name) match {
                case Some(mq) =>
                  val stn = c.typeMapper(SlickDriver).sqlTypeName
                  mq.sqlTypeName match {
                    case Some(t) if t != stn =>
                      checkFirst
                      b append "alter column" append quoteIdentifier(c.name) append ' '
                      b append "set data type " append stn
                    case _ =>
                  }
                case None =>
                  checkFirst
                  b append "add column "
                  createColumnDDLBuilder(c, table) appendColumn b
              }
            }
            for(c <- existingCols if !columns.exists(_.name == c.column)) {
              checkFirst
              b append "drop column " append quoteIdentifier(c.column)
            }
          case None =>
            b append "create table "
            b append quoteIdentifier(table.tableName) append " ("
            var first = true
            for(c <- columns) {
              if(first) first = false else b append ","
              c.appendColumn(b)
            }
            addTableOptions(b)
            b append ")"
        }
        b.toString
      }
    }*/
