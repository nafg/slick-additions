name := "slick-additions"

val settings = Seq(
  organization := "io.github.nafg",
  crossScalaVersions := Seq("2.10.6", "2.11.8"),
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-deprecation", "-unchecked")
)


lazy val `slick-additions-entity` = project.settings(settings)

lazy val `slick-additions` =
  (project in file("."))
    .dependsOn(`slick-additions-entity`)
    .aggregate(`slick-additions-entity`)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick" % "2.1.0",
        "org.scalatest" %% "scalatest" % "2.2.6" % "test",
        "com.h2database" % "h2" % "1.4.192" % "test",
        "ch.qos.logback" % "logback-classic" % "1.1.7" % "test"
      )
    )
