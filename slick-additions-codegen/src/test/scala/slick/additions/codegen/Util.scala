package slick.additions.codegen

import java.nio.file.Paths

import scala.concurrent.{ExecutionContext, Future}

import slick.future.Database
import slick.jdbc.{DatabaseConfig, H2Profile}

import com.typesafe.config.ConfigFactory


object Util {
  private val slickConfig = ConfigFactory.parseResources("config.conf")

  def writeToFile(generator: FileCodeGenerator) =
    generator.writeToFileSync(
      Paths.get(s"slick-additions-codegen/src/test/resources"),
      Util.slickConfig
    )

  def codeString(generator: FileCodeGenerator)(implicit executionContext: ExecutionContext): Future[String] =
    Database.use(DatabaseConfig.forConfig[H2Profile]("", slickConfig)) { db =>
      db.run(generator.codeStringFormatted(slickConfig.getString("profile")))
    }
}
