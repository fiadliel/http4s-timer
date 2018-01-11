package org.lyranthe.http4s.timer

import cats.effect.Sync

package object newrelic {
  implicit def newRelicTimer[F[_]: Sync]: NewRelicTimer[F] =
    new NewRelicTimer[F]
}
