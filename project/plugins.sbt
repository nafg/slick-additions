val sjsVer = sys.env.getOrElse("SCALAJS_VERSION", "1.9.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % sjsVer)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")

addSbtPlugin("io.github.nafg.mergify" % "sbt-mergify-github-actions" % "0.4.0")

