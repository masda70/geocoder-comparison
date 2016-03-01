name := "geocoder-comparison"

version := "0.0.1"

scalaVersion := "2.11.7"

organization := "com.masda70"

scalaSource in Compile := baseDirectory.value / "src/main"
scalaSource in Test := baseDirectory.value / "src/test"
javaSource in Compile := baseDirectory.value / "src/main"
javaSource in Test := baseDirectory.value / "src/test"

mainClass in Compile := Some("com.masda70.geocodercomparison.Compare")

val logLibraries = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0" ,
  "ch.qos.logback" % "logback-classic" % "1.1.3"
)

val akkaStreamLibrary = "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.1"

val json4sLibrary = "org.json4s" %% "json4s-jackson" % "3.2.10"

val scalaTestLibrary = "org.scalatest" %% "scalatest" % "2.2.4" % "test"

val commandLineParsingLibrary = "com.github.scopt" %% "scopt" % "3.3.0"

val sprayLibrary = "io.spray" %% "spray-client" % "1.3.3"

val kantaCsvJackson = "com.nrinaudo" %% "kantan.csv-jackson" % "0.1.8"

libraryDependencies ++= logLibraries ++ Seq(akkaStreamLibrary, json4sLibrary, scalaTestLibrary, commandLineParsingLibrary, sprayLibrary, kantaCsvJackson)

