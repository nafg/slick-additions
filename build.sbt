import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}


name := "slick-additions"

ThisBuild / crossScalaVersions := Seq("2.12.20", "2.13.16", "3.3.6")
ThisBuild / scalaVersion       := (ThisBuild / crossScalaVersions).value.last
ThisBuild / organization       := "io.github.nafg"
ThisBuild / scalacOptions ++=
  Seq("-deprecation", "-unchecked", "-feature") ++
    (if (scalaVersion.value.startsWith("2.12."))
       Seq("-language:higherKinds", "-Xsource:3")
     else if (scalaVersion.value.startsWith("2.13."))
       Seq("-Xsource:3-cross")
     else
       Seq())

lazy val `slick-additions-entity` =
  crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure)
    .settings()

val slickVersion = "3.6.1"

lazy val `slick-additions` =
  (project in file("."))
    .dependsOn(`slick-additions-entity`.jvm)
    .aggregate(
      `slick-additions-entity`.jvm,
      `slick-additions-entity`.js,
      `slick-additions-codegen`,
      `slick-additions-testcontainers`,
      `test-codegen`
    )
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick"           % slickVersion,
        "com.lihaoyi"        %% "sourcecode"      % "0.4.2",
        "org.scalatest"      %% "scalatest"       % "3.2.19"  % "test",
        "com.h2database"      % "h2"              % "2.3.232" % "test",
        "ch.qos.logback"      % "logback-classic" % "1.5.18"  % "test"
      )
    )

lazy val `slick-additions-codegen` =
  project
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
        ("org.scalameta"     %% "scalameta"      % "4.13.8")
          .cross(CrossVersion.for3Use2_13),
        ("org.scalameta"     %% "scalafmt-core"  % "3.9.9")
          .cross(CrossVersion.for3Use2_13),
        "com.h2database"      % "h2"             % "2.3.232" % "test",
        "org.scalatest"      %% "scalatest"      % "3.2.19"  % "test"
      )
    )

lazy val `test-codegen` =
  project
    .in(`slick-additions-codegen`.base / "src" / "test" / "resources")
    .dependsOn(LocalProject("slick-additions"))
    .settings(
      publish / skip                       := true,
      Compile / unmanagedSourceDirectories := Seq(baseDirectory.value),
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick" % slickVersion
      )
    )

lazy val `slick-additions-testcontainers` =
  project
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe"        % "config"     % "1.4.4",
        "com.typesafe.slick" %% "slick"      % slickVersion,
        "org.testcontainers"  % "postgresql" % "1.21.3"
      )
    )
