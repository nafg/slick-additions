package slick.additions.codegen

import scala.concurrent.ExecutionContext.Implicits.global


object CodeGen extends App {
  for (codeGeneration <- CodeGeneration.all)
    Util.writeToFile(codeGeneration)
}
