package slick.additions.codegen

import java.nio.file.Path


case class CodeGeneration(generator: FileCodeGenerator, rules: GenerationRules) {
  def pkgName          = generator.packageName
  val filename: String = generator.filePath(Path.of("")).toFile.getPath
}
object CodeGeneration                                                           {
  abstract class TestFileCodeGenerator(override val packageName: String, override val filename: String)
      extends FileCodeGenerator
  val all = Seq(
    CodeGeneration(
      new TestFileCodeGenerator("plain", "Tables") with TablesFileCodeGenerator with WrapInObjectFileCodeGenerator,
      new GenerationRules {}
    ),
    CodeGeneration(
      new TestFileCodeGenerator("plain", "Models") with ModelsFileCodeGenerator,
      new GenerationRules
    ),
    CodeGeneration(
      new TestFileCodeGenerator("entity", "TableModules")
        with EntityTableModulesFileCodeGenerator
        with WrapInObjectFileCodeGenerator,
      new GenerationRules with EntityGenerationRules
    ),
    CodeGeneration(
      new TestFileCodeGenerator("entity", "Models") {
        override protected def objectCodeGenerator(tableConfig: TableConfig): ObjectCodeGenerator =
          new KeylessModelsObjectCodeGenerator(tableConfig)
      },
      new GenerationRules with EntityGenerationRules
    )
  )
}
