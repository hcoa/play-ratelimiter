object Dependencies {
  val caffeine = "3.0.2"
  val bucket4j = "6.2.0"

  val guice = "5.0.1"
  // Test
  val scalatestPlusPlay = "5.1.0"
  val scalacheck = "1.15.3"

  val Scala212 = "2.12.13"
  val Scala213 = "2.13.5"

  val ScalaVersions = Seq(Scala212, Scala213)

  val PlayVersion: String = sys.props.getOrElse(
    "play.version",
    sys.env.getOrElse("PLAY_VERSION", "2.8.8")
  )
}
