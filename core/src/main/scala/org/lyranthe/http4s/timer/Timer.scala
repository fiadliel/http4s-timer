package org.lyranthe.http4s.timer

import org.http4s._

trait Timer[F[_]] {
  def time(serviceName: String,
           requestName: String,
           request: Request[F],
           response: F[Response[F]],
           user: Option[String] = None): F[Response[F]]
}
