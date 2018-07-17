package org.lyranthe.http4s.timer

import cats.Applicative
import cats.data.{Kleisli, OptionT}
import org.http4s._

object TimedAuthedService {
  def apply[T, F[_]: RequestTimer: Applicative](
      serviceName: String,
      authInfoToRemoteUser: T => Option[String])(
      pf: PartialFunction[AuthedRequest[F, T], (String, F[Response[F]])])
    : AuthedService[T, F] = {
    Kleisli(
      req =>
        pf.andThen(
            response =>
              OptionT.liftF(
                RequestTimer[F]
                  .time(serviceName,
                        response._1,
                        req.req,
                        authInfoToRemoteUser(req.authInfo))(response._2)))
          .applyOrElse(req, Function.const(OptionT.none)))
  }
}
