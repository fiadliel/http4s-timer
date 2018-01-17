package org.lyranthe.http4s.timer

import cats.Applicative
import cats.data.{Kleisli, OptionT}
import org.http4s._

object TimedService {
  def apply[F[_]: Timer: Applicative](serviceName: String)(
      pf: PartialFunction[Request[F], (String, F[Response[F]])])
    : HttpService[F] = {
    Kleisli(
      req =>
        pf.andThen(response =>
            OptionT.liftF(implicitly[Timer[F]]
              .time(serviceName, response._1, req)(response._2)))
          .applyOrElse(req, Function.const(OptionT.none)))
  }
}
