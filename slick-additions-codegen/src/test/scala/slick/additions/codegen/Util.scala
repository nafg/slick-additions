package slick.additions.codegen

import java.nio.file.Paths

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.ConfigFactory
import org.scalatest.CompleteLastly


object Util extends CompleteLastly {
  private val profile = slick.jdbc.H2Profile

  import profile.api.*


  private val slickConfig = ConfigFactory.parseResources("config.conf")
  private def db          = Database.forConfig("", slickConfig)

  def writeToFile(generator: FileCodeGenerator)(implicit executionContext: ExecutionContext) =
    generator.writeToFileSync(
      Paths.get(s"slick-additions-codegen/src/test/resources"),
      Util.slickConfig
    )

  def codeString(generator: FileCodeGenerator)(implicit executionContext: ExecutionContext)
    : Future[String] =
    complete {
      db.run(
        generator.codeStringFormatted(
          Util.slickConfig.getString("profile")
        )
      )
    }
      .lastly(db.close())
}
