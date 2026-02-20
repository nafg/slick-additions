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
  * are determined by the object configs (e.g. [[TableConfig]]s) and [[ColumnConfig]]s produced by the instance of
  * [[GenerationRules]] that is passed in.
  *
  * @see
  *   [[GenerationRules]]
  */
trait FileCodeGenerator {
  protected val generationRules: GenerationRules

  def packageName: String

  def filename: String

  // noinspection ScalaWeakerAccess
  protected def packageRef = toTermRef(packageName)

  def filePath(base: Path) = (packageName.split("\\.") :+ (filename + ".scala")).foldLeft(base)(_ resolve _)

  protected def imports: List[String] = Nil

  protected def importStatements(extraImports: List[String], slickProfileClass: Class[? <: JdbcProfile]): List[Stat] =
    makeImports(imports ++ extraImports)

  protected def objectCodeGenerator(objectConfig: generationRules.ObjectConfigType): ObjectCodeGenerator

  // noinspection ScalaWeakerAccess
  protected def objectStatements(objectConfig: generationRules.ObjectConfigType): List[Stat] =
    objectCodeGenerator(objectConfig).statements

  protected def fileStatements(objectConfigs: List[generationRules.ObjectConfigType]): List[Stat] =
    objectConfigs.flatMap(objectStatements)

  // noinspection ScalaWeakerAccess
  protected def fileStatement(objectConfigs: List[generationRules.ObjectConfigType], allImports: List[Stat]): Pkg =
    Pkg(
      ref = packageRef,
      body =
        Pkg.Body(
          allImports ++
            fileStatements(objectConfigs)
        )
    )

  protected def codeString(
    objectConfigs: List[generationRules.ObjectConfigType],
    extraImports: List[String],
    slickProfileClass: Class[? <: JdbcProfile]
  ): String =
    fileStatement(
      objectConfigs = objectConfigs,
      allImports = importStatements(extraImports, slickProfileClass)
    )
      .syntax

  def codeString(slickProfileClass: Class[? <: JdbcProfile])(implicit executionContext: ExecutionContext)
    : DBIO[String] =
    generationRules.objectConfigs(slickProfileClass)
      .map(codeString(_, generationRules.extraImports, slickProfileClass))

  def codeString(slickProfileClassName: String)(implicit executionContext: ExecutionContext)
    : DBIO[String] = codeString(Class.forName(slickProfileClassName).asSubclass(classOf[JdbcProfile]))

  def codeStringFormatted(
    slickProfileClassName: String,
    scalafmtConfig: ScalafmtConfig = ScalafmtConfig.defaultWithAlign
  )(implicit executionContext: ExecutionContext
  ): DBIO[String] =
    codeString(Class.forName(slickProfileClassName).asSubclass(classOf[JdbcProfile]))
      .flatMap { str =>
        val formatted = Scalafmt.format(str, scalafmtConfig)
        DBIO.from(Future.fromTry(formatted.toEither.toTry))
      }

  // noinspection ScalaWeakerAccess
  def writeToFileDBIO(
    baseDir: Path,
    slickConfig: Config
  )(implicit executionContext: ExecutionContext
  ) =
    codeStringFormatted(slickConfig.getString("profile")).map { codeStr =>
      val path = filePath(baseDir)
      Files.createDirectories(path.getParent)
      Files.write(path, codeStr.getBytes())
      path
    }

  def writeToFileSync(
    baseDir: Path,
    slickConfig: Config,
    timeout: Duration = Duration.Inf
  )(implicit executionContext: ExecutionContext
  ): Path = {
    val db = JdbcBackend.Database.forConfig("", slickConfig)

    try
      Await.result(db.run(writeToFileDBIO(baseDir, slickConfig)), timeout)
    finally
      db.close()
  }
}

trait BasicFileCodeGenerator  extends FileCodeGenerator {
  override protected val generationRules: BasicGenerationRules
}
trait EntityFileCodeGenerator extends FileCodeGenerator {
  override protected val generationRules: EntityGenerationRules
}
