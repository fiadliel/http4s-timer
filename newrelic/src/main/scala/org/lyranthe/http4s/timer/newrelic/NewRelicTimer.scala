package org.lyranthe.http4s.timer.newrelic

import cats.effect.Sync
import cats.implicits._
import com.newrelic.api.agent.{Segment => NRSegment, _}
import fs2._
import org.http4s.{EntityBody, Request}
import org.lyranthe.http4s.timer.Timer
import org.lyranthe.http4s.timer.newrelic.internal.{
  Http4sRequest,
  Http4sResponse
}
import scala.collection.JavaConverters._

class NewRelicTimer[F[_]](implicit F: Sync[F]) extends Timer[F] {
  @Trace(dispatcher = true)
  private def _startTransactionAndGetSegment(serviceName: String,
                                             requestName: String) = {
    NewRelic.getAgent.getTracedMethod.setMetricName(serviceName)
    NewRelic.getAgent.getTransaction.startSegment(requestName, "headers")
  }

  private[timer] def startTransactionAndGetSegment(serviceName: String,
                                                   requestName: String) =
    F.delay(_startTransactionAndGetSegment(serviceName, requestName))

  private[timer] def setRequestInfo(serviceName: String,
                                    requestName: String,
                                    transaction: Transaction,
                                    request: Request[F],
                                    user: Option[String]) = {
    F.delay {
      transaction.setWebRequest(new Http4sRequest(request, user))
      transaction.setTransactionName(TransactionNamePriority.CUSTOM_HIGH,
                                     true,
                                     serviceName,
                                     requestName)
    }
  }

  @Trace(async = true)
  private def _noticeError(segment: NRSegment,
                           t: Throwable,
                           attrs: Map[String, String]): Unit = {
    segment.getTransaction.getToken.linkAndExpire()
    NewRelic.noticeError(t, attrs.asJava, false)
  }

  private[timer] def noticeError(
      segment: NRSegment,
      t: Throwable,
      attrs: Map[String, String] = Map.empty): F[Unit] = {
    F.delay(_noticeError(segment, t, attrs))
  }

  private[timer] def setResponseInfo(
      segment: NRSegment,
      response: Either[Throwable, org.http4s.Response[F]])
    : F[org.http4s.Response[F]] = {
    response match {
      case Left(t) =>
        noticeError(segment, t) >> endSegment(segment) >> F.raiseError(t)

      case Right(response) =>
        val wrappedResponse = new Http4sResponse(response)

        F.delay {
          segment.getTransaction.setWebResponse(wrappedResponse)
          segment.getTransaction.addOutboundResponseHeaders()
          wrappedResponse.response
        }
    }
  }

  private[timer] def getBodySegment(transaction: Transaction,
                                    segmentName: String) =
    F.delay(transaction.startSegment(segmentName, "body"))

  def noticeErrorForStream[A](segment: NRSegment,
                              s: Stream[F, A]): Stream[F, A] =
    s.onError {
      case e => Stream.eval(noticeError(segment, e))
    }

  def endSegment(segment: NRSegment): F[Unit] = F.delay(segment.end())

  def startBodySegment(category: String, segment: NRSegment): F[NRSegment] = {
    F.delay(segment.getTransaction.startSegment(category, "body")) << endSegment(segment)
  }

  private[timer] def timeStream(segment: NRSegment,
                                category: String,
                                body: EntityBody[F]): EntityBody[F] =
    fs2.Stream.bracket(startBodySegment(category, segment))(
      noticeErrorForStream(_, body),
      endSegment)

  def time(serviceName: String,
           requestName: String,
           request: org.http4s.Request[F],
           response: F[org.http4s.Response[F]],
           user: Option[String] = None): F[org.http4s.Response[F]] = {
    for {
      segment <- startTransactionAndGetSegment(serviceName, requestName)
      _ <- setRequestInfo(serviceName,
                          s"$requestName (${request.method.name})",
                          segment.getTransaction,
                          request,
                          user)
      completedResponse <- response.attempt
      modifiedResponse <- setResponseInfo(segment, completedResponse)
      responseWithTimedBody = modifiedResponse.withBodyStream(
        timeStream(segment, requestName, modifiedResponse.body))
    } yield responseWithTimedBody
  }
}
