crossScalaVersions := Seq("2.10.5", "2.11.7")

scalaVersion := "2.11.7"

libraryDependencies += "com.typesafe.slick" %% "slick" % "3.0.0"

name := "slick-additions"

organization := "io.github.nafg"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.5" % "test"

libraryDependencies += "com.h2database" % "h2" % "1.4.187" % "test"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"

scalacOptions ++= Seq("-deprecation", "-unchecked")
