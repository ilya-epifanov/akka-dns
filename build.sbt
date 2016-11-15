organization := "ru.smslv.akka"

name := "akka-dns"

version := "2.4.2"

scalaVersion := "2.12.0"

crossScalaVersions := Seq("2.11.8", "2.12.0")

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.12",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.12" % Test,
  "org.scalatest" %% "scalatest" % "3.0.0" % Test
)
