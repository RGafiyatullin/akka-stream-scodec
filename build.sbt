name := "akka-stream-scodec"

version := "0.1.2.0"
organization := "com.github.rgafiyatullin"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
scalacOptions ++= Seq("-language:implicitConversions")
scalacOptions ++= Seq("-Ywarn-value-discard", "-Xfatal-warnings")

scalaVersion in ThisBuild := "2.12.1"

libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.4",
    "com.typesafe.akka" %% "akka-stream" % "2.5.7",
    "org.scodec" %% "scodec-core" % "1.10.3",
    "org.scodec" %% "scodec-akka" % "0.3.0",
    "com.github.rgafiyatullin"      %% "akka-stream-util" % "0.1.5.1"
  )

