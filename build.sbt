import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}


name := "slick-additions"

ThisBuild / crossScalaVersions := Seq("2.13.12", "3.3.1")
ThisBuild / scalaVersion       := (ThisBuild / crossScalaVersions).value.last
ThisBuild / organization       := "io.github.nafg"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-unchecked")

lazy val `slick-additions-entity` =
  crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure)
    .settings()

val slickVersion = "3.5.0-M5"

lazy val `slick-additions` =
  (project in file("."))
    .dependsOn(`slick-additions-entity`.jvm)
    .aggregate(`slick-additions-entity`.jvm, `slick-additions-entity`.js, `slick-additions-codegen`)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick"           % slickVersion,
        "com.lihaoyi"        %% "sourcecode"      % "0.3.1",
        "org.scalatest"      %% "scalatest"       % "3.2.17"  % "test",
        "com.h2database"      % "h2"              % "2.2.224" % "test",
        "ch.qos.logback"      % "logback-classic" % "1.4.14"  % "test"
      )
    )

lazy val `slick-additions-codegen` =
  project
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
        ("org.scalameta"     %% "scalameta"      % "4.8.14")
          .cross(CrossVersion.for3Use2_13).exclude("org.scala-lang.modules", "scala-collection-compat_2.13"),
        ("org.scalameta"     %% "scalafmt-core"  % "3.7.17")
          .cross(CrossVersion.for3Use2_13).exclude("org.scala-lang.modules", "scala-collection-compat_2.13"),
        "com.h2database"      % "h2"             % "2.2.224" % "test",
        "org.scalatest"      %% "scalatest"      % "3.2.17"  % "test"
      )
    )

lazy val `test-codegen` =
  project
    .in(`slick-additions-codegen`.base / "src" / "test" / "resources")
    .dependsOn(`slick-additions`)
    .settings(
      publish / skip                       := true,
      Compile / unmanagedSourceDirectories := Seq(baseDirectory.value),
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick" % slickVersion
      )
    )
