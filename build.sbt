organization := "ru.smslv.akka"

name := "akka-dns"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.11.8", "2.12.0-M4")

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

resolvers += "Typesafe snapshots" at "https://repo.typesafe.com/typesafe/snapshots/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.7",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.7" % "test",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)
