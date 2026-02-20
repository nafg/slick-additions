package slick.additions.codegen

/** Code generator for standard Slick table definitions. The generated code has no dependency on slick-additions.
  *
  * Tables that have more than 22 fields are mapped by simply nesting tuples so that no single tuple has more than 22
  * elements.
  */
trait TablesFileCodeGenerator extends SlickFileCodeGenerator with BasicFileCodeGenerator {
  override protected def objectCodeGenerator(tableConfig: TableConfig): TablesObjectCodeGenerator =
    new TablesObjectCodeGenerator(tableConfig)
}
