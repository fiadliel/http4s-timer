# http4s-timer

## Introduction
This adds timing capability to http4s, with a possible concrete implementation for New Relic

## New Relic Usage

Add to your library dependencies:

```scala
"org.lyranthe" % "http4s-timer-newrelic" % <version>
```

You should have the New Relic agent installed, with custom annotations enabled.

## Changes to HttpService[F]

If your initial version looks like:
```scala
val service = HttpService[IO] {
  case GET -> Root / "hello" / name =>
    Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
}
```
then this should be modified to:
```scala
import org.lyranthe.http4s.timer._
import org.lyranthe.http4s.timer.newrelic._

val service = TimedService[IO]("my_service") {
  case GET -> Root / "hello" / name =>
    "hello/:name" ->
      Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
}
```
The body of each partial function now returns a tuple of path name, and the contents of the resulting HTTP response. The path name cannot be taken directly from the requested path, as many paths vary in such things like user ID, but these should not be included in the path sent to monitoring.

## Changes to AuthedService[F]

Similarly, you should modify any `AuthedService` to use `TimedAuthedService`.

## Library Dependencies

The core library is dependent on `"org.http4s" %% "http4s-core" % "0.18.0-M8"`.

The newrelic library is also dependent on `"com.newrelic.agent.java" % "newrelic-api" % "3.45.0"`
