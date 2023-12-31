import sbt._

val commonSettings = Seq(
  autoCompilerPlugins := true,
  scalaVersion := "2.13.12",
  scalacOptions := Seq(
    "-release:21",
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-encoding",
    "utf8",
    "-Xfatal-warnings",
    "-Ymacro-annotations",
    "-Ytasty-reader",
    "-Ywarn-unused:imports"
  ),
  packageDoc / publishArtifact := false,
  libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.2"
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)

