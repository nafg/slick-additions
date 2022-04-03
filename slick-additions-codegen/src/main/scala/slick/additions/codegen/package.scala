package slick.additions

import scala.meta.{Defn, Mod}
import scala.util.Try


package object codegen {
  def snakeToCamel(s: String) = {
    def loop(cs: List[Char]): List[Char] =
      cs match {
        case '_' :: c :: rest => c.toUpper :: loop(rest)
        case c :: rest        => c :: loop(rest)
        case Nil              => Nil
      }

    loop(s.toList).mkString
  }

  class TryExtractor[A](f: String => A) {
    def unapply(string: String) = Try(f(string)).toOption
  }
  val AsBoolean = new TryExtractor(_.toBoolean)
  val AsInt = new TryExtractor(_.toInt)
  val AsDouble = new TryExtractor(_.toDouble)

  implicit class scalametaDefnClassExtensionMethods(private val self: Defn.Class) extends AnyVal {
    def withMod(mod: Mod) = self.copy(mods = mod +: self.mods)
  }
}
