lazy val myProject = (project in file(".")).enablePlugins(JavaAppPackaging, SbtTwirl)

name := "tombot"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.8",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.4.8",
  "net.virtual-void" %%  "json-lenses" % "0.6.1",
  "com.google.inject" % "guice" % "4.1.0",
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4",
  "btomala" %% "akka-http-twirl" % "1.1.0" excludeAll
    ExclusionRule(organization = "com.typesafe.akka")
)

unmanagedBase <<= baseDirectory { base => base / "libs" }

Revolver.settings

resolvers += "Bartek's repo at Bintray" at "https://dl.bintray.com/btomala/maven"
