package slick.additions.codegen

import slick.jdbc.meta.MQName


trait NamingRules {
  def columnNameToIdentifier(name: String): String = snakeToCamel(name)
  def tableNameToIdentifier(name: MQName): String  = snakeToCamel(name.name).capitalize
  def modelClassName(tableName: MQName): String
}

object NamingRules {

  /** $ModelSuffixedWithRow
    *
    * @define ModelSuffixedWithRow
    *
    * A naming rules implementation that suffixes model names with "Row".
    *
    * Assumes your database uses snake_case column names.
    *
    * Slick table names will be converted to camel case.
    *
    * Model names are the same as the corresponding Slick table but with "Row" appended.
    *
    * For example, if the database table is called `line_items`, the Slick table class will be called `LineItems` and
    * the model class will be called `LineItemsRow`.
    */
  trait ModelSuffixedWithRow extends NamingRules {
    override def modelClassName(tableName: MQName) = tableNameToIdentifier(tableName) + "Row"
  }

  /** $ModelSuffixedWithRow */
  object ModelSuffixedWithRow extends ModelSuffixedWithRow

  /** $TablePluralModelSingular
    *
    * @define TablePluralModelSingular
    *
    * Naming rules implementation that generates table classes in pluralized form and model classes in singular form.
    *
    * This is useful if you your database tables are named in the singular.
    *
    * For instance, if the database table is called `line_item`, the Slick table class will be called `LineItems` and
    * the model class will be called `LineItem`.
    */
  // noinspection ScalaUnusedSymbol
  trait TablePluralModelSingular extends NamingRules {
    override def tableNameToIdentifier(name: MQName) = {
      val base = super.tableNameToIdentifier(name)
      if (base.endsWith("s"))
        base
      else if (base.endsWith("x"))
        base + "es"
      else if (base.endsWith("y"))
        base.dropRight(1) + "ies"
      else
        base + "s"
    }

    override def modelClassName(tableName: MQName) = s"${snakeToCamel(tableName.name).capitalize}"
  }

  /** $TablePluralModelSingular */
  object TablePluralModelSingular extends TablePluralModelSingular
}
