package slick.additions.codegen

import java.nio.file.{Path, Paths}

import scala.concurrent.Future
import scala.io.Source

import slick.additions.codegen.extra.circe.CirceJsonCodecModelsCodeGenerator
import slick.additions.codegen.extra.monocle.MonocleLensesModelsCodeGenerator

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime


class CodegenTests extends AsyncFunSuite with BeforeAndAfterAll with ScalaFutures {

  import slick.jdbc.H2Profile.api._


  override implicit def patienceConfig: PatienceConfig = super.patienceConfig.copy(timeout = 10.seconds)

  val slickConfigHOCON =
    """profile = "slick.jdbc.H2Profile$"
      |driver = "org.h2.Driver"
      |url = "jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1"
      |""".stripMargin
  val slickConfig      = ConfigFactory.parseString(slickConfigHOCON)
  val db               = Database.forConfig("", slickConfig)

  override protected def beforeAll() =
    // noinspection SqlNoDataSourceInspection
    db.run(
      sqlu"""
        CREATE TABLE "colors" (
            "id"   BIGINT  NOT NULL PRIMARY KEY AUTO_INCREMENT,
            "name" VARCHAR NOT NULL
        );
        CREATE TABLE "people" (
            "id"          BIGINT  NOT NULL PRIMARY KEY AUTO_INCREMENT,
            "first"       VARCHAR NOT NULL,
            "last"        VARCHAR NOT NULL,
            "city"        VARCHAR NOT NULL DEFAULT 'New York',
            "date_joined" DATE    NOT NULL DEFAULT now(),
            "balance"     NUMERIC NOT NULL DEFAULT 0.0,
            "best_friend" BIGINT  NULL     REFERENCES "people" ("id") ON DELETE SET NULL,
            "col8"        FLOAT8  NULL,
            "col9"        BOOL    NULL,
            "col10"       INT     NULL,
            "col11"       INT     NULL,
            "col12"       INT     NULL,
            "col13"       INT     NULL,
            "col14"       INT     NULL,
            "col15"       INT     NULL,
            "col16"       INT     NULL,
            "col17"       INT     NULL,
            "col18"       INT     NULL,
            "col19"       INT     NULL,
            "col20"       INT     NULL,
            "col21"       INT     NULL,
            "col22"       INT     NULL,
            "col23"       INT     NULL,
            "col24"       INT     NULL
        );
       """
    ).futureValue
  override protected def afterAll() = db.close()

  def writeToFile(generator: BaseCodeGenerator, containerName: String): Future[Path] =
    db.run(
      generator.writeToFileDBIO(
        Paths.get("target"),
        slickConfig,
        new EntityGenerationRules {
          override def packageName: String = s"com.acme.${container.toLowerCase}"
          override def container: String   = containerName
        }
      )
    )

  def codeString(generator: BaseCodeGenerator, containerName: String): Future[String] =
    db.run(
      generator.codeStringFormatted(
        new EntityGenerationRules {
          override def packageName: String = s"com.acme.${container.toLowerCase}"
          override def container: String   = containerName
        },
        "slick.jdbc.H2Profile$"
      )
    )

  test("Tables") {
    codeString(new TablesCodeGenerator, "Tables")
      .map(assertResult(Source.fromResource("Tables.scala").mkString)(_))
  }

  test("EntityTableModules") {
    codeString(new EntityTableModulesCodeGenerator, "TableModules")
      .map(assertResult(Source.fromResource("TableModules.scala").mkString)(_))
  }

  test("Models") {
    codeString(
      new KeylessModelsCodeGenerator with MonocleLensesModelsCodeGenerator with CirceJsonCodecModelsCodeGenerator,
      "Models"
    )
      .map(assertResult(Source.fromResource("Models.scala").mkString)(_))
  }
}
