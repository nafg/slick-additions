import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}


name := "slick-additions"

ThisBuild / crossScalaVersions := Seq("2.13.12")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last
ThisBuild / organization := "io.github.nafg"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-unchecked")

lazy val `slick-additions-entity` =
  crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure)
    .settings()

val slickVersion = "3.5.0-M4"

lazy val `slick-additions` =
  (project in file("."))
    .dependsOn(`slick-additions-entity`.jvm)
    .aggregate(`slick-additions-entity`.jvm, `slick-additions-entity`.js, `slick-additions-codegen`)
    .settings(
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
        "com.typesafe.slick" %% "slick" % slickVersion,
        "com.lihaoyi" %% "sourcecode" % "0.3.1",
        "org.scalatest" %% "scalatest" % "3.2.17" % "test",
        "com.h2database" % "h2" % "2.2.224" % "test",
        "ch.qos.logback" % "logback-classic" % "1.4.11" % "test"
      )
    )

lazy val `slick-additions-codegen` =
  project
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
        "org.scalameta" %% "scalameta" % "4.8.12"
      )
    )
