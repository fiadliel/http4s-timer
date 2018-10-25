package org.lyranthe.http4s.timer

import cats.effect.Sync

package object newrelic {
  implicit def newRelicRequestTimer[F[_]: Sync]: NewRelicRequestTimer[F] =
    new NewRelicRequestTimer[F]
}
