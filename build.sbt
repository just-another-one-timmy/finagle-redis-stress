lazy val simple = (project in file("simple")).
  settings(
    name := "finagle-redis-stress-simple",
    version := "1.0",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.twitter" % "util-app_2.11" % "6.26.0",
      "com.twitter" % "finagle-core_2.11" % "6.26.0",
      "com.twitter" % "finagle-redis_2.11" % "6.26.0"
    )
  )

lazy val http = (project in file("http")).
  settings(
    name := "finagle-redis-stress-http",
    version := "1.0",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.twitter" % "util-app_2.11" % "6.26.0",
      "com.twitter" % "finagle-core_2.11" % "6.26.0",
      "com.twitter" % "finagle-redis_2.11" % "6.26.0",
      "com.twitter" % "finagle-httpx_2.11" % "6.26.0"
    )
  )

lazy val tw = (project in file("twitter-server")).
  settings(
    name := "finagle-redis-stress-tw",
    version := "1.0",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.twitter" % "util-app_2.11" % "6.26.0",
      "com.twitter" % "finagle-core_2.11" % "6.26.0",
      "com.twitter" % "finagle-redis_2.11" % "6.26.0",
      "com.twitter" % "finagle-httpx_2.11" % "6.26.0",
      "com.twitter" % "twitter-server_2.11" % "1.12.0"
    )
  )
