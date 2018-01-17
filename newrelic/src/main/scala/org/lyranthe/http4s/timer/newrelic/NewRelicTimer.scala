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
  private def _startExternalAndGetSegment(serviceName: String,
                                          requestName: String) = {
    NewRelic.getAgent.getTracedMethod.setMetricName(serviceName)
    NewRelic.getAgent.getTransaction.startSegment("External", requestName)
  }

  @Trace(dispatcher = true)
  private def _startWebTransactionAndGetSegment(serviceName: String,
                                                requestName: String) = {
    NewRelic.getAgent.getTracedMethod.setMetricName(serviceName)
    NewRelic.getAgent.getTransaction.startSegment("WebRequestPhase", "Headers")
  }

  private[timer] def startTransactionAndGetSegment(serviceName: String,
                                                   requestName: String,
                                                   isExternal: Boolean) =
    if (isExternal)
      F.delay(_startExternalAndGetSegment(serviceName, requestName))
    else
      F.delay(_startWebTransactionAndGetSegment(serviceName, requestName))

  private[timer] def setRequestInfo(requestName: String,
                                    transaction: Transaction,
                                    request: Request[F],
                                    user: Option[String]) = {
    F.delay {
      transaction.setWebRequest(new Http4sRequest(request, user))
      transaction.setTransactionName(TransactionNamePriority.CUSTOM_HIGH,
                                     true,
                                     "WebRequest",
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

  private[timer] def noticeErrorForStream[A](segment: NRSegment,
                                             s: Stream[F, A]): Stream[F, A] =
    s.onError {
      case e => Stream.eval(noticeError(segment, e))
    }

  private[timer] def endSegment(segment: NRSegment): F[Unit] =
    F.delay(segment.end())

  private[timer] def startBodySegment(segment: NRSegment): F[NRSegment] = {
    F.delay(segment.getTransaction.startSegment("WebRequestPhase", "Body")) <* endSegment(
      segment)
  }

  private[timer] def timeStream(segment: NRSegment,
                                body: EntityBody[F]): EntityBody[F] =
    fs2.Stream.bracket(startBodySegment(segment))(noticeErrorForStream(_, body),
                                                  endSegment)

  def time(serviceName: String,
           requestName: String,
           request: org.http4s.Request[F],
           user: Option[String] = None)(
      response: F[org.http4s.Response[F]]): F[org.http4s.Response[F]] = {
    for {
      segment <- startTransactionAndGetSegment(serviceName, requestName, false)
      _ <- setRequestInfo(s"$requestName (${request.method.name})",
                          segment.getTransaction,
                          request,
                          user)
      completedResponse <- response.attempt
      modifiedResponse <- setResponseInfo(segment, completedResponse)
      responseWithTimedBody = modifiedResponse.withBodyStream(
        timeStream(segment, modifiedResponse.body))
    } yield responseWithTimedBody
  }

  private[timer] def modifyExternalRequest[A](segment: NRSegment,
                                              completedRequest: F[A]) = {
    completedRequest.attempt flatMap {
      case Left(t) =>
        noticeError(segment, t) >> endSegment(segment) >> F.raiseError[A](t)
      case Right(result) =>
        endSegment(segment) >> F.pure(result)
    }
  }

  def timeExternal[A](serviceName: String, requestName: String)(
      action: F[A]): F[A] = {
    for {
      segment <- startTransactionAndGetSegment(serviceName, requestName, true)
      completedRequest <- modifyExternalRequest(segment, action)
    } yield completedRequest
  }
}
