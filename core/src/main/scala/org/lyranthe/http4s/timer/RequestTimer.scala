package org.lyranthe.http4s.timer

import com.newrelic.api.agent.ExternalParameters
import org.http4s._

trait RequestTimer[F[_]] {
  def time(
      serviceName: String,
      requestName: String,
      request: Request[F],
      user: Option[String] = None)(response: F[Response[F]]): F[Response[F]]

  def timeExternal(
      serviceName: String,
      requestName: String,
      request: Request[F])(response: F[Response[F]]): F[Response[F]]

  def timeExternal[A](
      serviceName: String,
      requestName: String,
      externalParameters: ExternalParameters)(action: F[A]): F[A]
}

object RequestTimer {
  def apply[F[_]: RequestTimer]: RequestTimer[F] = implicitly[RequestTimer[F]]
}
