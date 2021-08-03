val sjsVer = sys.env.getOrElse("SCALAJS_VERSION", "1.7.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % sjsVer)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")

addSbtPlugin("com.codecommit" % "sbt-github-actions" % "0.12.0")

libraryDependencies += "io.github.nafg.mergify" %% "mergify-writer" % "0.2.1"
