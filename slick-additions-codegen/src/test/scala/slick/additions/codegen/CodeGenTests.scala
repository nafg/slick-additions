package slick.additions.codegen

import java.nio.file.Path

import scala.io.Source

import org.scalatest.funsuite.AsyncFunSuite


class CodeGenTests extends AsyncFunSuite {
  private def filename(generator: FileCodeGenerator) = generator.filePath(Path.of("")).toFile.getPath
  for (generator <- TestFileCodeGenerator.all)
    test(filename(generator)) {
      Util.codeString(generator)
        .map(assertResult(_)(Source.fromResource(filename(generator)).mkString))
    }
}
