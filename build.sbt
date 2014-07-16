import sbt._

organization := "ru.smslv.akka"

name := "akka-dns"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.1"

crossScalaVersions := Seq("2.10.4", "2.11.1")

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Yinline-warnings")

libraryDependencies ++= Seq(
  "com.google.guava" % "guava" % "17.0",
  "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT",
  "com.typesafe.akka" %% "akka-testkit" % "2.4-SNAPSHOT" % "compile;test->test"
)

// lazy val akkaDns = Project("akka-dns", file(".")).dependsOn(akkaActors, akkaTestKit % "compile;test->test")

