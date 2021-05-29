lazy val commonSettings = Seq(
  scalaVersion := Dependencies.Scala213,
  crossScalaVersions := Dependencies.ScalaVersions,
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-Ywarn-unused:imports",
    "-Xlint:nullary-unit",
    "-Xlint",
    "-Ywarn-dead-code"
  ),
  javacOptions ++= Seq(
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  )
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    organization := "org.hcoa.playratelimiter",
    description := "Playframework rate-limiter",
    libraryDependencies ++= Seq(
      "com.github.ben-manes.caffeine" % "caffeine" % Dependencies.caffeine,
      "com.github.vladimir-bukhtoyarov" % "bucket4j-core" % Dependencies.bucket4j,
      "org.scalatestplus.play" %% "scalatestplus-play" % Dependencies.scalatestPlusPlay % Test,
      "com.typesafe.play" %% "play" % Dependencies.PlayVersion % Test,
      "com.typesafe.play" %% "play-specs2" % Dependencies.PlayVersion % Test
    )
  )
  .enablePlugins(PlayScala) //, JvmPlugin)