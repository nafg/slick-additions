package slick.additions.codegen

abstract class TestFileCodeGenerator(override val packageName: String, override val filename: String)
    extends FileCodeGenerator
object TestFileCodeGenerator {
  val all: Seq[TestFileCodeGenerator] =
    Seq(
      new TestFileCodeGenerator("plain", "Tables") with TablesFileCodeGenerator with WrapInObjectFileCodeGenerator {
        object generationRules extends BasicGenerationRules
      },
      new TestFileCodeGenerator("plain", "Models") with ModelsFileCodeGenerator                                    {
        object generationRules extends BasicGenerationRules
      },
      new TestFileCodeGenerator("entity", "TableModules")
        with EntityTableModulesFileCodeGenerator
        with WrapInObjectFileCodeGenerator                                                                         {
        object generationRules extends EntityGenerationRules
      },
      new TestFileCodeGenerator("entity", "Models") with EntityModelsFileCodeGenerator                             {
        object generationRules extends EntityGenerationRules
      }
    )
}
