organization := "ru.smslv.akka"

name := "akka-dns"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.10.4", "2.11.2")

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Yinline-warnings")

resolvers += "Typesafe snapshots" at "https://repo.typesafe.com/typesafe/snapshots/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT",
  "com.typesafe.akka" %% "akka-testkit" % "2.4-SNAPSHOT" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)
