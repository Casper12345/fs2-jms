ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.11"

lazy val root = (project in file("."))
  .settings(
    name := "fs2-jms",
    assemblyJarName := s"${name.value}_${scalaVersion.value.split('.').take(2).mkString(".")}-${version.value}.jar",
    libraryDependencies ++= Seq(
        Dependencies.fs2Core,
        Dependencies.catsEffect,
        Dependencies.scalaTest,
        Dependencies.scalaMock,
        Dependencies.catsEffectTesting,
        Dependencies.jmsSpec,
        Dependencies.scalaLogging,
        Dependencies.logbackClassic,
        Dependencies.activeMq,
        Dependencies.testContainers
    ),
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
        case PathList("META-INF", _*) => MergeStrategy.discard
        case "LICENSE-2.0.txt" => MergeStrategy.discard
        case _ => MergeStrategy.first
    },
    assembly /assemblyOption  := (assembly / assemblyOption).value.withIncludeScala(false)
  )
