import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

name := "slick-additions"

ThisBuild / crossScalaVersions := Seq("2.12.10", "2.13.1")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last

val settings = Seq(
  organization := "io.github.nafg",
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
        "com.typesafe.slick" %% "slick" % "3.3.2",
        "com.lihaoyi" %% "sourcecode" % "0.2.1",
        "org.scalatest" %% "scalatest" % "3.2.0" % "test",
        "com.h2database" % "h2" % "1.4.200" % "test",
        "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"
      )
    )
