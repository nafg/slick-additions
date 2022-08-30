import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}


name := "slick-additions"

ThisBuild / crossScalaVersions := Seq("2.12.16", "2.13.8")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last
ThisBuild / organization := "io.github.nafg"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-unchecked")

lazy val `slick-additions-entity` =
  crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure)
    .settings()

lazy val `slick-additions` =
  (project in file("."))
    .dependsOn(`slick-additions-entity`.jvm)
    .aggregate(`slick-additions-entity`.jvm, `slick-additions-entity`.js, `slick-additions-codegen`)
    .settings(
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
        "com.typesafe.slick" %% "slick" % "3.4.0-RC3",
        "com.lihaoyi" %% "sourcecode" % "0.3.0",
        "org.scalatest" %% "scalatest" % "3.2.13" % "test",
        "com.h2database" % "h2" % "2.1.214" % "test",
        "ch.qos.logback" % "logback-classic" % "1.2.11" % "test"
      )
    )

lazy val `slick-additions-codegen` =
  project
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick-hikaricp" % "3.4.0",
        "org.scalameta" %% "scalameta" % "4.5.13"
      )
    )
