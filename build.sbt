import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

name := "slick-additions"

val settings = Seq(
  organization := "io.github.nafg",
  crossScalaVersions := Seq("2.11.12", "2.12.8"),
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq("-deprecation", "-unchecked")
)

lazy val `slick-additions-entity` =
  crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure)
    .settings(settings: _*)

lazy val `slick-additions` =
  (project in file("."))
    .dependsOn(`slick-additions-entity`.jvm)
    .aggregate(`slick-additions-entity`.jvm, `slick-additions-entity`.js)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
        "com.typesafe.slick" %% "slick" % "3.3.0",
        "org.scalatest" %% "scalatest" % "3.0.6" % "test",
        "com.h2database" % "h2" % "1.4.199" % "test",
        "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
      )
    )
