crossScalaVersions := Seq("2.10.5", "2.11.6")

scalaVersion := "2.11.6"

libraryDependencies += "com.typesafe.slick" %% "slick" % "2.1.0"

name := "slick-additions"

organization := "io.github.nafg"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

libraryDependencies += "com.h2database" % "h2" % "1.3.170" % "test"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.28" % "test"
