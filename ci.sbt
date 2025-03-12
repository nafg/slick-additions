import _root_.io.github.nafg.mergify.dsl.*


mergifyExtraConditions := Seq(
  (Attr.Author :== "scala-steward") ||
    (Attr.Author :== "nafg-scala-steward[bot]")
)

val githubUrl = url("https://github.com/nafg/slick-additions")

inThisBuild(List(
  scmInfo                             :=
    Some(ScmInfo(browseUrl = githubUrl, connection = "scm:git:git@github.com/nafg/slick-additions.git")),
  homepage                            := Some(githubUrl),
  licenses                            := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  developers                          := List(
    Developer("nafg", "Naftoli Gugenheim", "98384+nafg@users.noreply.github.com", url("https://github.com/nafg"))
  ),
  dynverGitDescribeOutput ~= (_.map(o => o.copy(dirtySuffix = sbtdynver.GitDirtySuffix("")))),
  dynverSonatypeSnapshots             := true,
  githubWorkflowScalaVersions         := List("2.13.x", "3.x"),
  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowBuildPostamble ++=
    Seq(
      WorkflowStep.Sbt(
        commands = List(
          "slick-additions-codegen/Test/runMain slick.additions.codegen.CodeGen",
          "test-codegen/compile"
        ),
        name = Some("Check that codegen output compiles")
      ),
      WorkflowStep.Run(
        commands = List(
          "git diff --exit-code --quiet HEAD slick-additions-codegen/src/test/resources"
        ),
        name = Some("Check that codegen output hasn't changed")
      ),
      WorkflowStep.Run(List("mkdir -p slick-additions-entity/.js/target"))
    ),
  githubWorkflowJavaVersions          := Seq(JavaSpec.temurin("11")),
  githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
  githubWorkflowPublish               := Seq(
    WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
      )
    )
  )
))

sonatypeProfileName := "io.github.nafg"
