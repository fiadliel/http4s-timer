val scala212 = "2.12.9"
val scala213 = "2.13.0"

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
    scalaVersion := scala213,
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
    crossScalaVersions := List(scala212, scala213),
    libraryDependencies += "org.http4s" %% "http4s-core" % "0.21.0-M4",
    libraryDependencies += "com.newrelic.agent.java" % "newrelic-api" % "5.3.0"
  )

lazy val newrelic = project
  .in(file("newrelic"))
  .enablePlugins(GitVersioning)
  .settings(publishSettings)
  .settings(
    name := "http4s-timer-newrelic",
    crossScalaVersions := List(scala212, scala213)
  ) dependsOn core
