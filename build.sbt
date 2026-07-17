ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "cloud.anota"
ThisBuild / version      := "1.0.0"

lazy val root = (project in file("."))
  .settings(
    name := "anota-api",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.2" % Test
  )
