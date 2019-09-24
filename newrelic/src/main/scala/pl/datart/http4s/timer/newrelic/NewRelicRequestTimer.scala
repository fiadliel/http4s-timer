package pl.datart.http4s.timer
package newrelic

import java.net.URI

import cats.effect.Sync
import cats.implicits._
import com.newrelic.api.agent.{Segment => NRSegment, _}
import fs2._
import org.http4s.{EntityBody, Request, Response}
import pl.datart.http4s.timer.newrelic.internal.{
  Http4sRequest,
  Http4sResponse
}

import scala.collection.JavaConverters._
import scala.util.Try

class NewRelicRequestTimer[F[_]](implicit F: Sync[F]) extends RequestTimer[F] {
  @Trace(dispatcher = true)
  private def _startExternalAndGetSegment(
      routesName: String,
      requestName: String,
      externalParameters: ExternalParameters) = {
    val agent = NewRelic.getAgent
    val method = agent.getTracedMethod

    method.reportAsExternal(externalParameters)
    method.setMetricName(routesName)
    agent.getTransaction.startSegment("External", requestName)
  }

  @Trace(dispatcher = true)
  private def _startWebTransactionAndGetSegment(routesName: String) = {
    NewRelic.getAgent.getTracedMethod.setMetricName(routesName)
    NewRelic.getAgent.getTransaction.startSegment("WebRequestPhase", "Headers")
  }

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
    s.handleErrorWith { e =>
      Stream.eval_(noticeError(segment, e))
    }

  private[timer] def endSegment(segment: NRSegment): F[Unit] =
    F.delay(segment.end())

  private[timer] def startBodySegment(segment: NRSegment): F[NRSegment] = {
    F.delay(segment.getTransaction.startSegment("WebRequestPhase", "Body")) <* endSegment(
      segment)
  }

  private[timer] def timeStream(segment: NRSegment,
                                body: EntityBody[F]): EntityBody[F] =
    fs2.Stream.bracket(startBodySegment(segment))(endSegment).flatMap(noticeErrorForStream(_, body))


  def time(routesName: String,
           requestName: String,
           request: org.http4s.Request[F],
           user: Option[String] = None)(
      response: F[org.http4s.Response[F]]): F[org.http4s.Response[F]] = {
    for {
      segment <- F.delay(
        _startWebTransactionAndGetSegment(routesName))
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

  def timeExternal(
      routesName: String,
      requestName: String,
      request: Request[F])(response: F[Response[F]]): F[Response[F]] = {
    val uri = Try(URI.create(request.uri.toString())).toOption

    uri match {
      case Some(u) =>
        val externalParameters =
          HttpParameters
            .library("http4s")
            .uri(u)
            .procedure(request.method.name)
            .noInboundHeaders() // TOOD: add headers
            .build()

        for {
          segment <- F.delay(
            _startExternalAndGetSegment(routesName,
                                        requestName,
                                        externalParameters))
          completedRequest <- modifyExternalRequest(segment, response)
        } yield completedRequest

      case None => response
    }
  }

  def timeExternal[A](
      routesName: String,
      requestName: String,
      externalParameters: ExternalParameters)(action: F[A]): F[A] = {
    for {
      segment <- F.delay(
        _startExternalAndGetSegment(routesName,
                                    requestName,
                                    externalParameters))
      completedRequest <- modifyExternalRequest(segment, action)
    } yield completedRequest
  }
}
