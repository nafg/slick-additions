ThisBuild / publishTo := Some("slick-additions Bintray" at "https://api.bintray.com/maven/naftoligug/maven/slick-additions")

sys.env.get("BINTRAYKEY").toSeq map (key =>
  ThisBuild / credentials += Credentials("Bintray API Realm", "api.bintray.com", "naftoligug", key)
)
