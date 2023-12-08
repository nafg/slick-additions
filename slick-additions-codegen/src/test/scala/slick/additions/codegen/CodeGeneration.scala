package slick.additions.codegen

case class CodeGeneration(generator: BaseCodeGenerator, rules: GenerationRules) {
  def pkgName          = rules.packageName
  val filename: String = s"${pkgName}/${rules.container}.scala"
}
object CodeGeneration                                                           {
  class TestGenerationRules(override val container: String, override val packageName: String)
      extends GenerationRules
  val all = Seq(
    CodeGeneration(new TablesCodeGenerator, new TestGenerationRules("Tables", "plain")),
    CodeGeneration(new ModelsCodeGenerator, new TestGenerationRules("Models", "plain")),
    CodeGeneration(
      new EntityTableModulesCodeGenerator,
      new TestGenerationRules("TableModules", "entity") with EntityGenerationRules
    ),
    CodeGeneration(
      new KeylessModelsCodeGenerator,
      new TestGenerationRules("Models", "entity") with EntityGenerationRules
    )
  )
}
