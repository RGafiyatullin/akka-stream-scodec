
lazy val root = (project in file("."))
  .settings(
      name := "akka-stream-scodec",
      organization := "com.github.rgafiyatullin",
      version := BuildEnv.version,

      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      scalacOptions ++= Seq("-language:implicitConversions"),
      scalacOptions ++= Seq("-Ywarn-value-discard", "-Xfatal-warnings"),

      scalaVersion := BuildEnv.scalaVersion,

      libraryDependencies ++= Seq(
          "org.scalatest" %% "scalatest" % {
              scalaVersion.value match {
                  case v2_12 if v2_12.startsWith("2.12.") => "3.0.5"
                  case v2_11 if v2_11.startsWith("2.11.") => "2.2.6"
              }
          } % Test,

          "com.typesafe.akka"               %% "akka-stream" % "2.5.7",
          "org.scodec"                      %% "scodec-core" % "1.10.3",
          "org.scodec"                      %% "scodec-akka" % "0.3.0",
          "com.github.rgafiyatullin"        %% "akka-stream-util" % "0.1.5.1"
      ),

      publishTo := BuildEnv.publishTo,
      credentials ++= BuildEnv.credentials.toSeq
  )

