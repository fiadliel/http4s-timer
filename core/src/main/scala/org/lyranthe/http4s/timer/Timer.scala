package org.lyranthe.http4s.timer

import org.http4s._

trait Timer[F[_]] {
  def time(
      serviceName: String,
      requestName: String,
      request: Request[F],
      user: Option[String] = None)(response: F[Response[F]]): F[Response[F]]

  def timeExternal[A](serviceName: String, requestName: String)(
      action: F[A]): F[A]
}

object Timer {
  def apply[F[_]: Timer]: Timer[F] = implicitly[Timer[F]]
}
