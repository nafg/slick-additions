scalaVersion := "2.10.0-RC1"

scalaBinaryVersion <<= scalaVersion

libraryDependencies += "com.typesafe" % "slick" % "1.0.0-SNAPSHOT" cross CrossVersion.full

name := "slick-additions"

organization := "nafg"

