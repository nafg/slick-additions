publishMavenStyle in ThisBuild := true

publishTo in ThisBuild := Some("Scheduler Bintray" at "https://api.bintray.com/maven/naftoligug/maven/slick-additions")

sys.env.get("BINTRAYKEY").toSeq map (key =>
  credentials in ThisBuild += Credentials(
    "Bintray API Realm",
    "api.bintray.com",
    "naftoligug",
    key
  )
)
