package slick.additions.codegen

import scala.io.Source

import org.scalatest.funsuite.AsyncFunSuite


class CodeGenTests extends AsyncFunSuite {
  for (codeGeneration <- CodeGeneration.all)
    test(codeGeneration.filename) {
      Util.codeString(codeGeneration)
        .map(assertResult(Source.fromResource(codeGeneration.filename).mkString)(_))
    }
}
