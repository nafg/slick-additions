package slick.additions.codegen

import java.nio.file.{Files, Path}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.meta._

import slick.dbio.DBIO
import slick.jdbc.{JdbcBackend, JdbcProfile}

import com.typesafe.config.Config


/**
 * Base trait for code generators. Code generators are responsible for producing
 * actual code, but many of the details are determined by the [[TableConfig]]s and
 * [[ColumnConfig]]s produced by the instance of [[GenerationRules]] that is passed in.
 *
 * @see [[GenerationRules]]
 */
trait BaseCodeGenerator {
  private def toTermRef0(last: String, revInit: List[String]): Term.Ref = revInit match {
    case Nil     => Term.Name(last)
    case x :: xs => Term.Select(toTermRef0(x, xs), Term.Name(last))
  }

  protected def toTermRef(s: String): Term.Ref = {
    val last :: revInit = s.split('.').toList.reverse
    toTermRef0(last, revInit)
  }

  protected def toTypeRef(s: String): Type.Ref = {
    val last :: revInit = s.split('.').toList.reverse
    revInit match {
      case Nil     => Type.Name(last)
      case x :: xs => Type.Select(toTermRef0(x, xs), Type.Name(last))
    }
  }

  protected def imports(strings: List[String]): List[Stat] =
    if (strings.isEmpty)
      Nil
    else
      List(q"import ..${strings.map(_.parse[Importer].get)}")

  def codeString(rules: GenerationRules, slickProfileClass: Class[_ <: JdbcProfile])
                (implicit executionContext: ExecutionContext): DBIO[String]

  def codeString(rules: GenerationRules, slickProfileClassName: String)
                (implicit executionContext: ExecutionContext): DBIO[String] =
    codeString(rules, Class.forName(slickProfileClassName).asSubclass(classOf[JdbcProfile]))

  def doWriteToFile(baseDir: Path, slickConfig: Config, rules: GenerationRules)
                   (implicit executionContext: ExecutionContext): Path = {
    val path = rules.filePath(baseDir)
    val db = JdbcBackend.Database.forConfig("", slickConfig)
    val code =
      try Await.result(db.run(codeString(rules, slickConfig.getString("profile"))), Duration.Inf)
      finally db.close()
    Files.createDirectories(path.getParent)
    Files.write(path, code.getBytes())
  }
}
