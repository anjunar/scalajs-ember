addSbtPlugin("org.scala-js"  % "sbt-scalajs"  % "1.22.0")
// Signs the Maven Central bundle (`publishSigned`, scripts/publish-central.*). Same version as
// scalajs-ui, which publishes through the same sbt 2 staging path.
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
