package slick.additions.codegen

import scala.meta.Stat


trait ObjectCodeGenerator {
  def statements: List[Stat]
}
