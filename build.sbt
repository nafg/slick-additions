scalaVersion := "2.10.0"

libraryDependencies += "com.typesafe.slick" %% "slick" % "1.0.0"

name := "slick-additions"

organization := "nafg"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0.M6-SNAP5" % "test"

libraryDependencies += "com.h2database" % "h2" % "1.3.170" % "test"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "0.9.28" % "test"

libraryDependencies <+= (scalaVersion){ "org.scala-lang" % "scala-reflect" % _ }
