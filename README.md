# http4s-timer [![Build Status](https://travis-ci.com/eltherion/http4s-timer.svg?branch=master)](https://travis-ci.org/eltherion/http4s-timer) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)


## Introduction
This adds timing capability to http4s, with a possible concrete implementation for New Relic

## New Relic Usage

Add to your library dependencies:

```scala
"pl.datart" %% "http4s-timer-newrelic" % <version>
```

You should have the New Relic agent installed, with custom annotations enabled.

## Changes to HttpRoutes[F]

If your initial version looks like:
```scala
val routes = HttpRoutes[IO] {
  case GET -> Root / "hello" / name =>
    Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
}
```
then this should be modified to:
```scala
import pl.datart.http4s.timer._
import pl.datart.http4s.timer.newrelic._

val routes = TimedRoutes[IO]("my_routes") {
  case GET -> Root / "hello" / name =>
    "hello/:name" ->
      Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
}
```
The body of each partial function now returns a tuple of path name, and the contents of the resulting HTTP response. The path name cannot be taken directly from the requested path, as many paths vary in such things like user ID, but these should not be included in the path sent to monitoring.

## Changes to AuthedRoutes[F]

Similarly, you should modify any `AuthedRoutes` to use `TimedAuthedRoutes`.

## Library Dependencies

The core library is dependent on `"org.http4s" %% "http4s-core" % "0.21.0-M5"`.

The newrelic library is also dependent on `"com.newrelic.agent.java" % "newrelic-api" % "5.6.0"`

## Origin & credits

This repo is a fork of the original library from @fiadliel available at https://github.com/fiadliel/http4s-timer
