import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

name := "slick-additions"

ThisBuild / crossScalaVersions := Seq("2.12.12", "2.13.3")
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last
ThisBuild / organization := "io.github.nafg"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-unchecked")

val githubUrl = url("https://github.com/nafg/slick-additions")

ThisBuild / scmInfo := Some(
  ScmInfo(
    browseUrl = githubUrl,
    connection = "scm:git:git@github.com/nafg/slick-additions.git"
  )
)

ThisBuild / homepage := Some(githubUrl)

ThisBuild / developers +=
  Developer("nafg", "Naftoli Gugenheim", "98384+nafg@users.noreply.github.com", url("https://github.com/nafg"))

lazy val `slick-additions-entity` =
  crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure)
    .settings()

lazy val `slick-additions` =
  (project in file("."))
    .dependsOn(`slick-additions-entity`.jvm)
    .aggregate(`slick-additions-entity`.jvm, `slick-additions-entity`.js)
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
