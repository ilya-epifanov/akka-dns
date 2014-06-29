import sbt._

organization := "ru.smslv.akka"

name := "akka-dns"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.1")

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Yinline-warnings")

libraryDependencies ++= Seq(
  "com.google.guava" % "guava" % "17.0"
)

lazy val akkaActors = ProjectRef(uri("git://github.com/hajile/akka.git"), "akka-actor")

lazy val akkaTestKit = ProjectRef(uri("git://github.com/hajile/akka.git"), "akka-testkit")

lazy val akkaDns = Project("akka-dns", file(".")).dependsOn(akkaActors, akkaTestKit % "compile;test->test")

