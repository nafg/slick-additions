package slick.additions.codegen

object CodeGen extends App {
  for (codeGeneration <- TestFileCodeGenerator.all)
    Util.writeToFile(codeGeneration)
}
