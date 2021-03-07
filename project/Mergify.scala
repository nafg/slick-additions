import java.io.{File, FileInputStream}
import java.{util => ju}

import scala.collection.JavaConverters._
import scala.util.matching.Regex

import org.yaml.snakeyaml.Yaml
import sbt.AutoPlugin
import sbt.io.IO
import sbtghactions.GenerativeKeys.{githubWorkflowGenerate, githubWorkflowGeneratedCI}
import sbtghactions.GenerativePlugin


object Mergify extends AutoPlugin {
  override def requires = GenerativePlugin
  override def trigger = allRequirements

  type Dict = ju.Map[String, AnyRef]

  override def projectSettings = Seq(
    githubWorkflowGenerate := {
      githubWorkflowGenerate.value
      githubWorkflowGeneratedCI.value
        .find(_.id == "build")
        .foreach { job =>
          val yaml = new Yaml
          val res = yaml.load[Dict](new FileInputStream(".mergify.yml"))
          val conditions =
            res
              .get("pull_request_rules").asInstanceOf[ju.List[Dict]].asScala.map(_.asScala)
              .find(_ ("name") == "Automatically merge successful scala-steward PRs").get
              .apply("conditions").asInstanceOf[ju.List[String]].asScala

          val existing = conditions.filter(_.matches(s"status-success=${Regex.quote(job.name)} \\(.*\\)"))
          conditions --= existing

          val concreteJobNames = for (o <- job.oses; s <- job.scalas; v <- job.javas) yield s"${job.name} ($o, $s, $v)"

          conditions ++= concreteJobNames.map(name => s"status-success=$name")

          IO.write(new File(".mergify.yml"), yaml.dumpAsMap(res))
        }
    }
  )
}
