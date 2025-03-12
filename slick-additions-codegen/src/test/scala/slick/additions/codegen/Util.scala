package slick.additions.codegen

import java.nio.file.Paths

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.ConfigFactory
import org.scalatest.CompleteLastly


object Util extends CompleteLastly {
  private val profile = slick.jdbc.H2Profile

  import profile.api._


  private val slickConfig = ConfigFactory.parseResources("config.conf")
  private def db          = Database.forConfig("", slickConfig)

  def writeToFile(generation: CodeGeneration)(implicit executionContext: ExecutionContext) =
    generation.generator.writeToFileSync(
      Paths.get(s"slick-additions-codegen/src/test/resources/${generation.pkgName}"),
      Util.slickConfig,
      generation.rules
    )

  def codeString(generation: CodeGeneration)(implicit executionContext: ExecutionContext): Future[String] =
    complete {
      db.run(
        generation.generator.codeStringFormatted(
          generation.rules,
          Util.slickConfig.getString("profile")
        )
      )
    }
      .lastly(db.close())
}
