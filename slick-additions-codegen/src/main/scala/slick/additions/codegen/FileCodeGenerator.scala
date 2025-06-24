package slick.additions.codegen

import java.nio.file.{Files, Path}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.meta.{Pkg, Stat, XtensionSyntax}

import slick.additions.codegen.ScalaMetaDsl.*
import slick.dbio.DBIO
import slick.jdbc.{JdbcBackend, JdbcProfile}

import com.typesafe.config.Config
import org.scalafmt.Scalafmt
import org.scalafmt.config.ScalafmtConfig


/** Base trait for code generators. Code generators are responsible for producing actual code, but many of the details
  * are determined by the [[TableConfig]]s and [[ColumnConfig]]s produced by the instance of [[GenerationRules]] that is
  * passed in.
  *
  * @see
  *   [[GenerationRules]]
  */
trait FileCodeGenerator {
  def packageName: String

  def filename: String

  // noinspection ScalaWeakerAccess
  protected def packageRef = toTermRef(packageName)

  def filePath(base: Path) = (packageName.split("\\.") :+ (filename + ".scala")).foldLeft(base)(_ resolve _)

  protected def imports: List[String] = Nil

  protected def importStatements(extraImports: List[String], slickProfileClass: Class[? <: JdbcProfile]): List[Stat] =
    makeImports(imports ++ extraImports)

  protected def objectCodeGenerator(tableConfig: TableConfig): ObjectCodeGenerator

  // noinspection ScalaWeakerAccess
  protected def objectStatements(tableConfig: TableConfig): List[Stat] = objectCodeGenerator(tableConfig).statements

  protected def fileStatements(tableConfigs: List[TableConfig]): List[Stat] = tableConfigs.flatMap(objectStatements)

  // noinspection ScalaWeakerAccess
  protected def fileStatement(tableConfigs: List[TableConfig], allImports: List[Stat]): Pkg =
    Pkg(
      ref = packageRef,
      body =
        Pkg.Body(
          allImports ++
            fileStatements(tableConfigs)
        )
    )

  def codeString(
    tableConfigs: List[TableConfig],
    extraImports: List[String],
    slickProfileClass: Class[? <: JdbcProfile]
  ): String =
    fileStatement(
      tableConfigs = tableConfigs,
      allImports = importStatements(extraImports, slickProfileClass)
    )
      .syntax

  def codeString(
    rules: GenerationRules,
    slickProfileClass: Class[_ <: JdbcProfile]
  )(implicit executionContext: ExecutionContext
  ): DBIO[String] =
    rules.tableConfigs(slickProfileClass)
      .map(codeString(_, rules.extraImports, slickProfileClass))

  def codeString(rules: GenerationRules, slickProfileClassName: String)(implicit executionContext: ExecutionContext)
    : DBIO[String] = codeString(rules, Class.forName(slickProfileClassName).asSubclass(classOf[JdbcProfile]))

  def codeStringFormatted(
    rules: GenerationRules,
    slickProfileClassName: String,
    scalafmtConfig: ScalafmtConfig = ScalafmtConfig.defaultWithAlign
  )(implicit executionContext: ExecutionContext
  ): DBIO[String] =
    codeString(rules, Class.forName(slickProfileClassName).asSubclass(classOf[JdbcProfile]))
      .flatMap { str =>
        val formatted = Scalafmt.format(str, scalafmtConfig)
        DBIO.from(Future.fromTry(formatted.toEither.toTry))
      }

  // noinspection ScalaWeakerAccess
  def writeToFileDBIO(
    baseDir: Path,
    slickConfig: Config,
    rules: GenerationRules
  )(implicit executionContext: ExecutionContext
  ) =
    codeStringFormatted(rules, slickConfig.getString("profile")).map { codeStr =>
      val path = filePath(baseDir)
      Files.createDirectories(path.getParent)
      Files.write(path, codeStr.getBytes())
      path
    }

  def writeToFileSync(
    baseDir: Path,
    slickConfig: Config,
    rules: GenerationRules,
    timeout: Duration = Duration.Inf
  )(implicit executionContext: ExecutionContext
  ): Path = {
    val db = JdbcBackend.Database.forConfig("", slickConfig)

    try
      Await.result(db.run(writeToFileDBIO(baseDir, slickConfig, rules)), timeout)
    finally
      db.close()
  }
}
