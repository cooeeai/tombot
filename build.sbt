enablePlugins(JavaAppPackaging)

name := "tombot"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.8",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.4.8",
  "net.virtual-void" %%  "json-lenses" % "0.6.1",
  "com.google.inject" % "guice" % "4.1.0",
  "net.codingwell" %% "scala-guice" % "4.0.1"
)

Revolver.settings
