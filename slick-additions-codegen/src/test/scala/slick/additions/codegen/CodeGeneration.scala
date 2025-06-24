package slick.additions.codegen

import java.nio.file.Path


case class CodeGeneration(generator: BaseCodeGenerator, rules: GenerationRules) {
  def pkgName          = generator.packageName
  val filename: String = generator.filePath(Path.of("")).toFile.getPath
}
object CodeGeneration                                                           {
  abstract class TestGenerator(override val packageName: String, override val filename: String)
      extends BaseCodeGenerator
  val all = Seq(
    CodeGeneration(
      new TestGenerator("plain", "Tables") with TablesCodeGenerator with WrapInObjectCodeGenerator,
      new GenerationRules {}
    ),
    CodeGeneration(
      new TestGenerator("plain", "Models") with ModelsCodeGenerator,
      new GenerationRules {}
    ),
    CodeGeneration(
      new TestGenerator("entity", "TableModules") with EntityTableModulesCodeGenerator with WrapInObjectCodeGenerator,
      new GenerationRules with EntityGenerationRules
    ),
    CodeGeneration(
      new TestGenerator("entity", "Models") with KeylessModelsCodeGenerator,
      new GenerationRules with EntityGenerationRules
    )
  )
}
