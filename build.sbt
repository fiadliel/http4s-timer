val scala211 = "2.11.11"
val scala212 = "2.12.4"

sonatypeProfileName := "org.lyranthe"
pomExtra in Global := {
  <url>https://github.com/fiadliel/http4s-timer</url>
  <licenses>
    <license>
      <name>MIT</name>
      <url>https://github.com/fiadliel/http4s-timer/blob/master/LICENSE</url>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>fiadliel</id>
      <name>Gary Coady</name>
      <url>https://www.lyranthe.org/</url>
    </developer>
  </developers>
}

inThisBuild(
  List(
    organization := "org.lyranthe",
    scalaVersion := "2.12.4",
    scalacOptions ++= Seq("-Ypartial-unification"),
    git.useGitDescribe := true
  )
)

val publishSettings = List(
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  )
)

lazy val core = project
  .in(file("core"))
  .enablePlugins(GitVersioning)
  .settings(publishSettings)
  .settings(
    name := "http4s-timer-core",
    crossScalaVersions := List(scala211, scala212),
    libraryDependencies += "org.http4s" %% "http4s-core" % "0.18.1"
  )

lazy val newrelic = project
  .in(file("newrelic"))
  .enablePlugins(GitVersioning)
  .settings(publishSettings)
  .settings(
    name := "http4s-timer-newrelic",
    crossScalaVersions := List(scala211, scala212),
    libraryDependencies += "com.newrelic.agent.java" % "newrelic-api" % "3.47.0"
  ) dependsOn core
