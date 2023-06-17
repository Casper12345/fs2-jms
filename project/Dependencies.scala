import sbt.*

object Dependencies {

  val fs2Core = "co.fs2" %% "fs2-core" % "3.7.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.4.8"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15" % Test
  val scalaMock = "org.scalamock" %% "scalamock" % "5.2.0" % Test
  val catsEffectTesting = "org.typelevel" %% "cats-effect-testing-scalatest"  % "1.5.0" % Test
  val jmsSpec =  "org.apache.geronimo.specs" % "geronimo-jms_2.0_spec" % "1.0-alpha-2"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.4.7"
  val activeMq = "org.apache.activemq" % "activemq-client" % "5.18.1" % Test
  val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.12" % Test

}
